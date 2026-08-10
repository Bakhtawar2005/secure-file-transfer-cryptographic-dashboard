import datetime
import os
import yfinance as yf
import pandas as pd
import numpy as np
from database import SessionLocal, init_db, Fund, FundNAV, StockPrice
import ta
from ta.trend import sma_indicator, macd, macd_signal
from ta.momentum import rsi
from ta.volatility import bollinger_hband, bollinger_lband, average_true_range
from ta.volume import on_balance_volume
from sklearn.preprocessing import MinMaxScaler

# ==========================================
# 1. DEFINE THE STOCKS WE WANT TO FETCH
# ==========================================
# We track the top 5 companies on the Pakistan Stock Exchange + FFC.
# Yahoo Finance tracks them using the ".KA" suffix (Karachi).
TICKERS = {
    "Systems Limited": "SYS.KA",
    "Meezan Bank": "MEBL.KA",
    "Hub Power Company": "HUBC.KA",
    "Engro Corporation": "ENGRO.KA",
    "Oil \u0026 Gas Development Co": "OGDC.KA",
    "Fauji Fertilizer Company": "FFC.KA"
}

def clean_float(val):
    """Safely cast value to float or return None if NaN."""
    if pd.isna(val) or val is None:
        return None
    return float(val)

def clean_int(val):
    """Safely cast value to int or return None if NaN."""
    if pd.isna(val) or val is None:
        return None
    return int(val)

def scrape_and_save_stocks():
    print("====================================================")
    print("PSX STOCKS OHLCV \u0026 TECHNICAL INDICATORS INGESTION")
    print("====================================================")
    
    # 10 years of daily history is ideal for technical indicators
    print("Connecting to Yahoo Finance to fetch 10 years of PSX stock data...")
    
    db = SessionLocal()
    
    # Resolve correct paths whether script is run from project root or backend folder
    base_path = ""
    if os.path.basename(os.getcwd()) != "backend":
        base_path = "backend"
        
    csv_dir = os.path.join(base_path, "data", "stocks")
    os.makedirs(csv_dir, exist_ok=True)
    
    try:
        # Initialize SQLite database tables first (creates StockPrice if not exists)
        init_db()
        
        all_dfs = {}
        
        for company_name, ticker_symbol in TICKERS.items():
            print(f"\n--- Processing {company_name} ({ticker_symbol}) ---")
            
            # Fetch historical data using yfinance (10 years)
            ticker_obj = yf.Ticker(ticker_symbol)
            df_history = ticker_obj.history(period="10y")
            
            if df_history.empty:
                print(f"Warning: No data returned for {ticker_symbol}")
                continue
                
            print(f"Fetched {len(df_history)} daily bars.")
            
            # ----------------------------------------------------
            # A. Calculate Technical Indicators \u0026 Volatility (Risk)
            # ----------------------------------------------------
            print("Calculating technical indicators...")
            
            # 1. Trend: SMA 20
            df_history['sma_20'] = sma_indicator(df_history['Close'], window=20)
            
            # 2. Momentum: RSI 14
            df_history['rsi_14'] = rsi(df_history['Close'], window=14)
            
            # 3 \u0026 4. Trend/Momentum: MACD Line, MACD Signal \u0026 MACD Histogram
            df_history['macd'] = macd(df_history['Close'])
            df_history['macd_signal'] = macd_signal(df_history['Close'])
            df_history['macd_hist'] = df_history['macd'] - df_history['macd_signal']
            
            # 5 \u0026 6. Volatility limit: Bollinger Bands Upper \u0026 Lower
            df_history['bb_high'] = bollinger_hband(df_history['Close'], window=20)
            df_history['bb_low'] = bollinger_lband(df_history['Close'], window=20)
            
            # 7. Volume indicator: On-Balance Volume (OBV)
            df_history['obv'] = on_balance_volume(df_history['Close'], df_history['Volume'])
            
            # 8. Volatility indicator: Average True Range (ATR 14)
            df_history['atr_14'] = average_true_range(df_history['High'], df_history['Low'], df_history['Close'], window=14)
            
            # 9. Extra Trend: EMA 50
            from ta.trend import ema_indicator
            df_history['ema_50'] = ema_indicator(df_history['Close'], window=50)
            
            # 10. Extra Technical Features: Daily Return and High-Low Price Spread
            df_history['daily_return'] = df_history['Close'].pct_change()
            df_history['high_low_spread'] = (df_history['High'] - df_history['Low']) / df_history['Close']
            
            # 11. Risk Metric: Rolling 30-Day Volatility (annualized)
            df_history['rolling_volatility_30d'] = df_history['daily_return'].rolling(window=30).std() * np.sqrt(252)
            
            # 12. Feature Scaling: Min-Max Scaling (bounds numeric features between 0 and 1)
            print("Scaling features...")
            cols_to_scale = [
                'Open', 'High', 'Low', 'Close', 'Volume', 
                'sma_20', 'rsi_14', 'macd', 'macd_signal', 'macd_hist', 
                'bb_high', 'bb_low', 'obv', 'atr_14', 'ema_50', 
                'daily_return', 'high_low_spread', 'rolling_volatility_30d'
            ]
            scaler = MinMaxScaler()
            df_scale = df_history[cols_to_scale].copy()
            df_scale_filled = df_scale.ffill().bfill().fillna(0)
            scaled_array = scaler.fit_transform(df_scale_filled)
            
            for idx, col in enumerate(cols_to_scale):
                scaled_series = pd.Series(scaled_array[:, idx], index=df_history.index)
                scaled_series[df_history[col].isna()] = np.nan
                df_history[f"{col}_scaled"] = scaled_series
            
            # Reset index to make Date a column instead of the index
            df_history = df_history.reset_index()
            
            # Remove timezone awareness from Date column for Excel compatibility
            if df_history['Date'].dt.tz is not None:
                df_history['Date'] = df_history['Date'].dt.tz_localize(None)

            # Store for consolidated excel export
            all_dfs[ticker_symbol] = df_history.copy()

            
            # ----------------------------------------------------
            # B. Export to Organized Clean Files
            # ----------------------------------------------------
            # Save to individual CSV
            csv_path = os.path.join(csv_dir, f"{ticker_symbol}.csv")
            df_history.to_csv(csv_path, index=False)
            print(f"-\u003e Saved individual CSV: {csv_path}")
            
            # Save as a separate Excel file
            excel_path = os.path.join(csv_dir, f"{ticker_symbol}.xlsx")
            df_history.to_excel(excel_path, index=False)
            print(f"-\u003e Saved individual Excel file: {excel_path}")
            
            # ----------------------------------------------------
            # C. Save Data to SQLite Database
            # ----------------------------------------------------
            print("Syncing data to SQLite database...")
            
            # Backwards compatibility check: ensure Fund exists
            fund = db.query(Fund).filter(Fund.name == company_name).first()
            if not fund:
                fund = Fund(
                    name=company_name,
                    category="PSX Stock",
                    risk_level="High",
                    is_islamic=(company_name in ["Meezan Bank", "Systems Limited", "Hub Power Company"]),
                    fund_size_mkr=10000.0
                )
                db.add(fund)
                db.commit()
                db.refresh(fund)
                
            # Sync daily prices to FundNAV (for simple charts support) and StockPrice (full columns)
            navs_synced = 0
            stock_prices_synced = 0
            
            for _, row in df_history.iterrows():
                nav_date = row["Date"].date()
                
                # C1. Update FundNAV (Backward compatibility)
                existing_nav = db.query(FundNAV).filter(
                    FundNAV.fund_id == fund.id,
                    FundNAV.date == nav_date
                ).first()
                if not existing_nav:
                    new_nav = FundNAV(
                        fund_id=fund.id,
                        date=nav_date,
                        nav=round(row["Close"], 2)
                    )
                    db.add(new_nav)
                    navs_synced += 1
                
                # C2. Update new StockPrice model with full details
                existing_sp = db.query(StockPrice).filter(
                    StockPrice.ticker == ticker_symbol,
                    StockPrice.date == nav_date
                ).first()
                if not existing_sp:
                    new_sp = StockPrice(
                        ticker=ticker_symbol,
                        company_name=company_name,
                        date=nav_date,
                        open=clean_float(row["Open"]),
                        high=clean_float(row["High"]),
                        low=clean_float(row["Low"]),
                        close=clean_float(row["Close"]),
                        volume=clean_int(row["Volume"]),
                        sma_20=clean_float(row.get("sma_20")),
                        rsi_14=clean_float(row.get("rsi_14")),
                        macd=clean_float(row.get("macd")),
                        macd_signal=clean_float(row.get("macd_signal")),
                        macd_hist=clean_float(row.get("macd_hist")),
                        bb_high=clean_float(row.get("bb_high")),
                        bb_low=clean_float(row.get("bb_low")),
                        obv=clean_float(row.get("obv")),
                        atr_14=clean_float(row.get("atr_14")),
                        ema_50=clean_float(row.get("ema_50")),
                        daily_return=clean_float(row.get("daily_return")),
                        high_low_spread=clean_float(row.get("high_low_spread")),
                        rolling_volatility_30d=clean_float(row.get("rolling_volatility_30d"))
                    )
                    db.add(new_sp)
                    stock_prices_synced += 1
                    
            db.commit()
            print(f"-> Database sync: Added {navs_synced} FundNAV bars, {stock_prices_synced} StockPrice bars.")
            
        # Save consolidated Excel file with sheets for each company
        consolidated_path = os.path.join(base_path, "data", "psx_stock_data.xlsx")
        print(f"\nConsolidating all 6 companies into multiple sheets in: {consolidated_path}...")
        with pd.ExcelWriter(consolidated_path, engine="openpyxl") as writer:
            for ticker, df_hist in all_dfs.items():
                df_hist.to_excel(writer, sheet_name=ticker, index=False)
        print(f"-> Saved consolidated Excel file: {consolidated_path}")
        
        # Also copy it to the stocks directory
        stocks_consolidated_path = os.path.join(csv_dir, "psx_stock_data.xlsx")
        import shutil
        shutil.copy(consolidated_path, stocks_consolidated_path)
        print(f"-> Saved copy of consolidated Excel to: {stocks_consolidated_path}")
            
        print(f"\nSuccess! 10-year stock data and indicators saved in separate CSV and XLSX files in: {csv_dir}")
        
    except Exception as e:
        print(f"\nError occurred during stock scraping & computation: {e}")
    finally:
        db.close()
        
if __name__ == "__main__":
    scrape_and_save_stocks()