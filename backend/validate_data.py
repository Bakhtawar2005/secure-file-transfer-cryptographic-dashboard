from database import SessionLocal, Fund, FundNAV, MarketIndicator, NewsArticle, StockPrice

def validate_database():
    print("=============================================")
    print("RUNNING AL-MUTABIR DATABASE VALIDATION CHECKS")
    print("=============================================")
    db = SessionLocal()
    try:
        funds_count = db.query(Fund).count()
        navs_count = db.query(FundNAV).count()
        indicators_count = db.query(MarketIndicator).count()
        news_count = db.query(NewsArticle).count()
        stocks_count = db.query(StockPrice).count()
        
        print(f" -> Mutual Funds: {funds_count} row(s)")
        print(f" -> Fund NAV Prices: {navs_count} row(s)")
        print(f" -> Macro Market Indicators: {indicators_count} row(s)")
        print(f" -> Financial News Articles: {news_count} row(s)")
        print(f" -> PSX Daily Stock Prices: {stocks_count} row(s)")
        
        errors = 0
        if funds_count == 0:
            print("WARNING: Table 'funds' is empty!")
            errors += 1
        if navs_count == 0:
            print("WARNING: Table 'fund_navs' is empty!")
            errors += 1
        if indicators_count == 0:
            print("WARNING: Table 'market_indicators' is empty!")
            errors += 1
        if news_count == 0:
            print("WARNING: Table 'news_articles' is empty!")
            errors += 1
        if stocks_count == 0:
            print("WARNING: Table 'stock_prices' is empty!")
            errors += 1
            
        if errors == 0:
            print("\nSUCCESS: All database tables are populated and healthy!")
        else:
            print(f"\nWARNING: Database has {errors} validation failure(s). Run ingestion scrapers to seed.")
            
    except Exception as e:
        print(f"Validation error: {e}")
    finally:
        db.close()
    print("=============================================")

if __name__ == "__main__":
    validate_database()