import os
import joblib
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, r2_score
from sqlalchemy.orm import Session
from database import SessionLocal
from features import get_engineered_features

# Paths to save the trained model artifacts
MODEL_PATH = "backend/model.joblib" if os.path.exists("backend") else "model.joblib"
FEATURES_LIST_PATH = "backend/features_list.joblib" if os.path.exists("backend") else "features_list.joblib"

# The input clues (features) we want the AI model to learn from
FEATURE_COLUMNS = [
    "return_7d",
    "return_30d",
    "volatility_30d",
    "trend_30d",
    "kibor_6m",
    "inflation_rate",
    "exchange_rate_usd",
    "kse100_return_30d",
    "rolling_sentiment_7d"
]

TARGET_COLUMN = "target_return_30d"

def train_recommender_model(db: Session):
    """
    Loads features, splits data, trains a Random Forest Regressor,
    evaluates it, and saves the trained model to disk.
    """
    print("Loading engineered features from database...")
    df = get_engineered_features(db)
    
    if df.empty:
        print("No training data available.")
        return None
        
    # We can only train on rows that have a target label (past data where next 30 days are known)
    # Rows without a target label represent the most recent 30 days (unknown future return), which we predict later
    train_data = df.dropna(subset=[TARGET_COLUMN])
    
    if len(train_data) < 100:
        print(f"Not enough training samples: {len(train_data)}. Need at least 100.")
        return None
        
    X = train_data[FEATURE_COLUMNS]
    y = train_data[TARGET_COLUMN]
    
    # Split data into 80% training and 20% testing sets
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
    
    print(f"Training Random Forest Regressor on {len(X_train)} samples...")
    # Initialize the Random Forest model
    model = RandomForestRegressor(n_estimators=100, max_depth=10, random_state=42)
    model.fit(X_train, y_train)
    
    # Make predictions on the test set to evaluate performance
    y_pred = model.predict(X_test)
    
    # Calculate evaluation metrics
    mae = mean_absolute_error(y_test, y_pred)
    r2 = r2_score(y_test, y_pred)
    
    print("\n--- AI Model Evaluation ---")
    print(f"Mean Absolute Error (MAE): {mae:.6f} ({mae*100:.4f}%)")
    print(f"R-squared (R2) Score: {r2:.4f}")
    
    # Print feature importance (which clues were the most useful to the AI)
    print("\nFeature Importance (Clues Ranking):")
    importances = model.feature_importances_
    for col, imp in sorted(zip(FEATURE_COLUMNS, importances), key=lambda x: x[1], reverse=True):
        print(f" - {col}: {imp*100:.2f}%")
        
    # Save the trained model and feature list to disk
    joblib.dump(model, MODEL_PATH)
    joblib.dump(FEATURE_COLUMNS, FEATURES_LIST_PATH)
    print(f"\nTrained model successfully saved to: {MODEL_PATH}")
    
    return model

def predict_future_returns(db: Session):
    """
    Loads the saved model and predicts the expected returns for the next 30 days
    using the latest available features for all funds.
    """
    if not os.path.exists(MODEL_PATH):
        print("Model file not found. Train the model first.")
        return pd.DataFrame()
        
    # Load model and features list
    model = joblib.load(MODEL_PATH)
    feature_cols = joblib.load(FEATURES_LIST_PATH)
    
    # Load all current data features
    df = get_engineered_features(db)
    if df.empty:
        return pd.DataFrame()
        
    # For prediction, we take the LATEST row for each fund (which represents today's status)
    latest_rows = []
    for fund_id, group in df.groupby("fund_id"):
        latest_rows.append(group.iloc[-1])
        
    df_latest = pd.DataFrame(latest_rows)
    
    # Feed clues into the saved model to predict the next 30-day returns
    X_latest = df_latest[feature_cols]
    df_latest["predicted_return_30d"] = model.predict(X_latest)
    
    return df_latest[["fund_id", "fund_name", "fund_category", "fund_risk_level", "fund_is_islamic", "predicted_return_30d", "volatility_30d"]]

if __name__ == "__main__":
    db = SessionLocal()
    try:
        # Train model
        train_recommender_model(db)
        
        # Test a prediction
        print("\nTesting prediction on latest data...")
        preds = predict_future_returns(db)
        if not preds.empty:
            print(preds.head()[["fund_name", "fund_category", "predicted_return_30d"]])
    finally:
        db.close()
