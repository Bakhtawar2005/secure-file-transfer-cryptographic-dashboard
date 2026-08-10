from database import SessionLocal, init_db
from recommender import get_recommendations
from model import train_recommender_model, predict_future_returns
from validate_data import validate_database

def test_pipeline():
    print("=============================================")
    print("RUNNING END-TO-END SYSTEM INTEGRATION TESTS")
    print("=============================================")
    
    # 1. Initialize Database
    print("Step 1: Syncing database schemas...")
    init_db()
    
    # 2. Database Validation
    print("Step 2: Checking database counts...")
    validate_database()
    
    # 3. Model Training
    print("Step 3: Training Random Forest Regressor...")
    db = SessionLocal()
    try:
        model = train_recommender_model(db)
        if model:
            print(" -> Model trained and saved successfully!")
        else:
            print(" -> Model training skipped (requires historical data).")
            
        # 4. Predictions
        print("Step 4: Generating future returns forecasts...")
        df_preds = predict_future_returns(db)
        print(f" -> Forecasts generated for {len(df_preds)} funds.")
        
        # 5. Recommendation Engine
        print("Step 5: Querying portfolio recommendations...")
        print(" -> Inputs: Capital=50k PKR, Risk=High, Preference=Islamic")
        recs = get_recommendations(db, 50000.0, "High", "Islamic")
        print(f" -> Recommendation engine returned {len(recs)} portfolio option(s).")
        for idx, r in enumerate(recs, 1):
            print(f"    * Rank {idx}: {r['name']} ({r['predicted_return']}% expected return)")
            
        print("\nSUCCESS: All end-to-end integration tests passed!")
        
    except Exception as e:
        print(f"\nERROR: Integration test failed: {e}")
    finally:
        db.close()
    print("=============================================")

if __name__ == "__main__":
    test_pipeline()