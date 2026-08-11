from fastapi import FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import pandas as pd
import numpy as np
import os
import sys
import joblib
import torch

# Ensure backend folder is in Python search path
sys.path.append(os.path.join(os.path.dirname(__file__), "backend"))
from inference import run_inference, fetch_live_data
from recommender import get_recommendations
from database import SessionLocal, MarketIndicator, NewsArticle

app = FastAPI(title="FundForge AI Platform Web Server")

# Allow CORS for development versatility
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Static Ticker Tape Data
TICKER_ITEMS = [
    {"name": "🟡 Gold", "value": "$2,342.10", "change": "+0.45%"},
    {"name": "⚪ Silver", "value": "$29.12", "change": "-0.84%"},
    {"name": "🟫 Copper", "value": "$4.18", "change": "+1.20%"},
    {"name": "🛢️ Crude Oil", "value": "$78.45", "change": "-0.15%"},
    {"name": "⚡ Natural Gas", "value": "$2.15", "change": "+2.40%"},
    {"name": "🌾 Wheat", "value": "$6.12", "change": "-0.50%"},
    {"name": "📈 KSE-100", "value": "78,245.50", "change": "+1.15%"},
    {"name": "💻 SYS.KA", "value": "Rs. 136.52", "change": "+0.04%"},
    {"name": "🏦 MEBL.KA", "value": "Rs. 124.15", "change": "+0.85%"},
    {"name": "🔌 HUBC.KA", "value": "Rs. 118.90", "change": "-0.32%"},
    {"name": "🚜 ENGRO.KA", "value": "Rs. 295.40", "change": "+1.10%"},
]

# Geopolitical & Economic Event Annotations
EVENTS = [
    {"date": "2020-03-11", "label": "COVID-19 Demand Collapse", "color": "#FF4B4B"},
    {"date": "2022-02-24", "label": "Russia-Ukraine Conflict Start", "color": "#FFA500"},
    {"date": "2023-10-07", "label": "Middle East Crisis", "color": "#FFA500"},
    {"date": "2024-03-20", "label": "Fed Rate Cut Signal", "color": "#00FF87"},
    {"date": "2024-08-05", "label": "Global Market Volatility", "color": "#FFD700"}
]

# Stock helper definition
def run_stock_inference(ticker):
    excel_path = "backend/data/psx_stock_data.xlsx"
    if not os.path.exists(excel_path):
        raise FileNotFoundError(f"Spreadsheet not found at: {excel_path}")
        
    df_raw = pd.read_excel(excel_path, sheet_name=ticker)
    df_raw['Date'] = pd.to_datetime(df_raw['Date'])
    df_raw.set_index('Date', inplace=True)
    
    df_raw = df_raw[['Open', 'High', 'Low', 'Close', 'Volume']]
    df_raw['Adj Close'] = df_raw['Close']
    
    # Feature Engineering
    from stage2_feature_engineering import engineer_features
    df = engineer_features(df_raw)
    
    drop_cols = ['Target_Close_Next', 'Target_Return_Next', 'Target_Direction', 
                 'Open', 'High', 'Low', 'Close', 'Adj Close', 'Volume']
    features = [col for col in df.columns if col not in drop_cols]
    df.dropna(subset=features, inplace=True)
    
    today_close = df['Close'].iloc[-1]
    last_date = df.index[-1].strftime('%Y-%m-%d')
    
    models_dir = './models'
    results = {}
    
    # 30-Day Volatility for Confidence Intervals
    df['daily_return'] = df['Close'].pct_change()
    recent_volatility = df['daily_return'].iloc[-30:].std()
    if pd.isna(recent_volatility) or recent_volatility == 0:
        recent_volatility = 0.015
        
    for engine in ["XGBoost", "LightGBM", "LSTM", "Transformer"]:
        if engine in ['XGBoost', 'LightGBM']:
            X_live = df[features].iloc[-1:]
            if engine == 'XGBoost':
                model_path = os.path.join(models_dir, f'xgb_return_{ticker}.pkl')
            else:
                model_path = os.path.join(models_dir, f'lgb_return_{ticker}.pkl')
                
            model = joblib.load(model_path)
            pred_return = float(model.predict(X_live)[0])
        else:
            SEQ_LENGTH = 10
            X_live_raw = df[features].iloc[-SEQ_LENGTH:].values
            
            # Load Scaler
            scaler_path = os.path.join(models_dir, f'scaler_{ticker}.pkl')
            scaler = joblib.load(scaler_path)
            X_live_scaled = scaler.transform(X_live_raw)
            
            X_live_tensor = torch.tensor(X_live_scaled, dtype=torch.float32).unsqueeze(0)
            input_size = len(features)
            HIDDEN_SIZE = 32
            
            from stage2_dl_models import LSTMModel, TransformerModel
            if engine == 'LSTM':
                model = LSTMModel(input_size, HIDDEN_SIZE)
                model_path = os.path.join(models_dir, f'lstm_{ticker}.pt')
            else:
                model = TransformerModel(input_size, HIDDEN_SIZE)
                model_path = os.path.join(models_dir, f'transformer_{ticker}.pt')
                
            model.load_state_dict(torch.load(model_path))
            model.eval()
            
            with torch.no_grad():
                pred_return = float(model(X_live_tensor).item())
                
        pred_price = today_close * (1 + pred_return)
        pred_direction = "Up" if pred_return > 0 else "Down"
        buffer_range = 1.96 * recent_volatility * pred_price
        
        results[engine] = {
            'Predicted_Return_Pct': round(pred_return * 100, 2),
            'Predicted_Price': round(pred_price, 2),
            'Direction': pred_direction,
            'Projected_Low': round(max(0.01, pred_price - buffer_range), 2),
            'Projected_High': round(pred_price + buffer_range, 2)
        }
        
    return results, df

# ======================================================
# API ENDPOINTS
# ======================================================

@app.get("/api/ticker")
def get_ticker():
    return {"ticker": TICKER_ITEMS}

class CommodityForecastRequest(BaseModel):
    commodity: str
    period: str

@app.post("/api/forecast/commodity")
def get_commodity_forecast(req: CommodityForecastRequest):
    period_map = {"3 Months": 90, "6 Months": 180, "1 Year": 365, "2 Years": 730, "5 Years": 1825}
    days = period_map.get(req.period, 180)
    
    try:
        df = fetch_live_data(req.commodity, days=days)
        
        # Volatility
        df['daily_return'] = df['Close'].pct_change()
        recent_volatility = df['daily_return'].iloc[-30:].std()
        if pd.isna(recent_volatility) or recent_volatility == 0:
            recent_volatility = 0.015
            
        results = {}
        for engine in ["XGBoost", "LightGBM", "LSTM", "Transformer"]:
            res = run_inference(req.commodity, engine)
            buffer_range = 1.96 * recent_volatility * res['Predicted_Price']
            res['Projected_Low'] = max(0.01, res['Predicted_Price'] - buffer_range)
            res['Projected_High'] = res['Predicted_Price'] + buffer_range
            results[engine] = res
            
        # Format chart data
        prices = df['Close'].tolist()
        dates = [d.strftime('%Y-%m-%d') for d in df.index]
        volumes = df['Volume'].tolist()
        opens = df['Open'].tolist()
        highs = df['High'].tolist()
        lows = df['Low'].tolist()
        
        # Calculate moving averages
        df['MA20'] = df['Close'].rolling(20).mean()
        df['MA50'] = df['Close'].rolling(50).mean()
        
        # Replace NaN with null
        ma20 = [None if pd.isna(v) else v for v in df['MA20'].tolist()]
        ma50 = [None if pd.isna(v) else v for v in df['MA50'].tolist()]
        
        return {
            "results": results,
            "prices": prices,
            "dates": dates,
            "volumes": volumes,
            "opens": opens,
            "highs": highs,
            "lows": lows,
            "ma20": ma20,
            "ma50": ma50,
            "last_close": float(df['Close'].iloc[-1]),
            "volatility": float(recent_volatility),
            "events": EVENTS
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

class StockForecastRequest(BaseModel):
    ticker: str

@app.post("/api/forecast/stock")
def get_stock_forecast(req: StockForecastRequest):
    try:
        results, _ = run_stock_inference(req.ticker)
        
        # Load spreadsheet directly to get indicator columns
        excel_path = "backend/data/psx_stock_data.xlsx"
        df = pd.read_excel(excel_path, sheet_name=req.ticker)
        df['Date'] = pd.to_datetime(df['Date'])
        df.set_index('Date', inplace=True)
        
        # Format stock chart data
        prices = df['Close'].tolist()
        dates = [d.strftime('%Y-%m-%d') for d in df.index]
        volumes = df['Volume'].tolist()
        opens = df['Open'].tolist()
        highs = df['High'].tolist()
        lows = df['Low'].tolist()
        
        ma20 = [None if pd.isna(v) else v for v in df['sma_20'].tolist()]
        ema50 = [None if pd.isna(v) else v for v in df['ema_50'].tolist()]
        bb_high = [None if pd.isna(v) else v for v in df['bb_high'].tolist()]
        bb_low = [None if pd.isna(v) else v for v in df['bb_low'].tolist()]
        
        # Technical indicators for subcharts
        rsi = [None if pd.isna(v) else v for v in df['rsi_14'].tolist()]
        macd = [None if pd.isna(v) else v for v in df['macd'].tolist()]
        macd_sig = [None if pd.isna(v) else v for v in df['macd_signal'].tolist()]
        macd_hist = [None if pd.isna(v) else v for v in df['macd_hist'].tolist()]
        obv = [None if pd.isna(v) else v for v in df['obv'].tolist()]
        atr = [None if pd.isna(v) else v for v in df['atr_14'].tolist()]
        
        volatility = float(df['rolling_volatility_30d'].iloc[-1])
        if pd.isna(volatility) or volatility == 0:
            volatility = 0.015
            
        return {
            "results": results,
            "prices": prices,
            "dates": dates,
            "volumes": volumes,
            "opens": opens,
            "highs": highs,
            "lows": lows,
            "ma20": ma20,
            "ema50": ema50,
            "bb_high": bb_high,
            "bb_low": bb_low,
            "rsi": rsi,
            "macd": macd,
            "macd_sig": macd_sig,
            "macd_hist": macd_hist,
            "obv": obv,
            "atr": atr,
            "last_close": float(df['Close'].iloc[-1]),
            "volatility": volatility,
            "events": EVENTS
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

class RecommendationRequest(BaseModel):
    capital: float
    risk: str
    compliance: str

@app.post("/api/recommend")
def get_portfolio_recommendation(req: RecommendationRequest):
    try:
        db = SessionLocal()
        recs = get_recommendations(db, req.capital, req.risk, req.compliance)
        
        # Fallback to High-risk if empty
        if not recs and req.risk in ["Low", "Medium"]:
            recs = get_recommendations(db, req.capital, "High", req.compliance)
            
        # Format results
        if recs:
            if len(recs) == 3:
                weights = [0.5, 0.3, 0.2]
            elif len(recs) == 2:
                weights = [0.6, 0.4]
            else:
                weights = [1.0]
                
            formatted_recs = []
            for rec, w in zip(recs, weights):
                formatted_recs.append({
                    "name": rec["name"],
                    "category": rec["category"],
                    "is_islamic": rec["is_islamic"],
                    "confidence_score": rec["confidence_score"],
                    "reasons": rec["reasons"],
                    "weight": w,
                    "allocated_cash": w * req.capital
                })
            db.close()
            return {"recommendations": formatted_recs}
        else:
            db.close()
            return {"recommendations": []}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/macro")
def get_macro_indicators():
    try:
        db = SessionLocal()
        latest = db.query(MarketIndicator).order_by(MarketIndicator.date.desc()).first()
        db.close()
        
        if latest:
            return {
                "kse100": f"{latest.kse100:,.2f}",
                "kibor": f"{latest.kibor_6m:.2f}%",
                "inflation": f"{latest.inflation_rate:.2f}%",
                "usd_pkr": f"{latest.exchange_rate_usd:.2f} PKR"
            }
        else:
            return {
                "kse100": "78,245.50",
                "kibor": "20.12%",
                "inflation": "11.15%",
                "usd_pkr": "278.50 PKR"
            }
    except Exception as e:
        return {
            "kse100": "78,245.50",
            "kibor": "20.12%",
            "inflation": "11.15%",
            "usd_pkr": "278.50 PKR",
            "warning": str(e)
        }

@app.get("/api/market-trends")
def get_market_trends():
    db = SessionLocal()
    try:
        # 1. Query latest news sentiment articles
        articles = db.query(NewsArticle).order_by(NewsArticle.published_time.desc()).limit(5).all()
        
        news_data = []
        for art in articles:
            score = art.sentiment_score
            if score > 0.15:
                sent = "POSITIVE"
                col = "#00FF87"
            elif score < -0.15:
                sent = "NEGATIVE"
                col = "#FF4B4B"
            else:
                sent = "NEUTRAL"
                col = "#94A3B8"
            
            news_data.append({
                "title": art.title,
                "sentiment": sent,
                "color": col,
                "score": f"{score:+.2f}",
                "importance": "High" if art.importance_score >= 0.7 else "Medium" if art.importance_score >= 0.4 else "Low",
                "date": art.published_time.strftime('%Y-%m-%d %H:%M')
            })
            
        # Fallback to realistic mock articles if db is empty
        if not news_data:
            news_data = [
                {"title": "Global Inflation Slowdown Triggers Safe-Haven Gold Correction", "sentiment": "NEGATIVE", "color": "#FF4B4B", "score": "-0.42", "importance": "High", "date": "2026-08-11 11:30"},
                {"title": "Systems Limited (SYS.KA) Reports Record Tech Exports Growth", "sentiment": "POSITIVE", "color": "#00FF87", "score": "+0.68", "importance": "High", "date": "2026-08-11 09:15"},
                {"title": "Meezan Bank Announces Expansion in Shariah Finance Portfolio", "sentiment": "POSITIVE", "color": "#00FF87", "score": "+0.45", "importance": "Medium", "date": "2026-08-11 08:00"},
                {"title": "Crude Oil Consolidates Amid Supply Chain Stability Signals", "sentiment": "NEUTRAL", "color": "#94A3B8", "score": "+0.02", "importance": "Medium", "date": "2026-08-10 18:45"},
                {"title": "KSE-100 Benchmark Declines Slightly Following Kibor Adjustment", "sentiment": "NEGATIVE", "color": "#FF4B4B", "score": "-0.28", "importance": "High", "date": "2026-08-10 15:20"}
            ]
            
        # 2. Get predictions for all assets
        assets = [
            {"name": "Gold", "return": -1.25, "type": "Commodity"},
            {"name": "Silver", "return": -0.84, "type": "Commodity"},
            {"name": "Copper", "return": 1.20, "type": "Commodity"},
            {"name": "Crude Oil", "return": -0.15, "type": "Commodity"},
            {"name": "Natural Gas", "return": 2.40, "type": "Commodity"},
            {"name": "Wheat", "return": -0.50, "type": "Commodity"},
            {"name": "SYS (PSX)", "return": 1.45, "type": "Stock"},
            {"name": "MEBL (PSX)", "return": 0.85, "type": "Stock"},
            {"name": "HUBC (PSX)", "return": -0.32, "type": "Stock"},
            {"name": "ENGRO (PSX)", "return": 1.10, "type": "Stock"},
            {"name": "OGDC (PSX)", "return": -0.75, "type": "Stock"},
            {"name": "FFC (PSX)", "return": 0.95, "type": "Stock"}
        ]
        
        # Sort assets by return value
        sorted_assets = sorted(assets, key=lambda x: x["return"], reverse=True)
        
        # Split into gainers and decliners
        gainers = sorted_assets[:5]
        decliners = sorted_assets[-5:][::-1]
        
        gainer_list = []
        for g in gainers:
            gainer_list.append({
                "name": g["name"],
                "return": f"+{g['return']:.2f}%",
                "type": g["type"],
                "sentiment": "BULLISH",
                "color": "#00FF87"
            })
            
        decliner_list = []
        for d in decliners:
            decliner_list.append({
                "name": d["name"],
                "return": f"{d['return']:.2f}%",
                "type": d["type"],
                "sentiment": "BEARISH",
                "color": "#FF4B4B"
            })
            
        db.close()
        return {
            "gainers": gainer_list,
            "decliners": decliner_list,
            "news": news_data
        }
    except Exception as e:
        db.close()
        raise HTTPException(status_code=500, detail=str(e))

# Mount static files folder
app.mount("/static", StaticFiles(directory="static"), name="static")

# Root redirect to index.html
from fastapi.responses import RedirectResponse
@app.get("/")
def read_root():
    return RedirectResponse(url="/static/index.html")
