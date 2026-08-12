import os
import sys
import pandas as pd
import numpy as np
import warnings
import joblib
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset
from sklearn.preprocessing import StandardScaler
from xgboost import XGBRegressor
from lightgbm import LGBMRegressor

# Add backend directory to sys.path
sys.path.append(os.path.join(os.path.dirname(__file__), "backend"))

# Import deep learning model architectures, training helper, and settings from stage 2 DL script
from stage2_dl_models import LSTMModel, TransformerModel, train_model, create_sequences
from stage2_feature_engineering import engineer_features

warnings.filterwarnings('ignore')

# Set random seeds for reproducibility
torch.manual_seed(42)
np.random.seed(42)

# Training Hyperparameters
SEQ_LENGTH = 10
BATCH_SIZE = 32
EPOCHS = 100
PATIENCE = 10
LR = 0.001
HIDDEN_SIZE = 32

STOCKS = ["SYS.KA", "MEBL.KA", "HUBC.KA", "ENGRO.KA", "OGDC.KA", "FFC.KA"]
excel_path = "backend/data/psx_stock_data.xlsx"
models_dir = "./models"
os.makedirs(models_dir, exist_ok=True)

def train_stock_pipelines():
    if not os.path.exists(excel_path):
        print(f"Error: consolidated stock data excel file not found at {excel_path}")
        return

    print("====================================================")
    print("TRAINING MULTI-MODEL ML/DL PIPELINES FOR PSX STOCKS")
    print("====================================================")

    for ticker in STOCKS:
        print(f"\n--- Training Ticker: {ticker} ---")
        
        # 1. Read sheet from Excel
        df_raw = pd.read_excel(excel_path, sheet_name=ticker)
        df_raw['Date'] = pd.to_datetime(df_raw['Date'])
        df_raw.set_index('Date', inplace=True)
        
        # Keep only raw columns before engineering features
        df_raw = df_raw[['Open', 'High', 'Low', 'Close', 'Volume']]
        # yfinance Adj Close fallback
        df_raw['Adj Close'] = df_raw['Close']
        
        # 2. Run feature engineering
        df = engineer_features(df_raw)
        
        drop_cols = ['Target_Close_Next', 'Target_Return_Next', 'Target_Direction', 
                     'Open', 'High', 'Low', 'Close', 'Adj Close', 'Volume']
        features = [col for col in df.columns if col not in drop_cols]
        
        # Clean rows with NaN in features
        df.dropna(subset=features + ['Target_Return_Next', 'Target_Close_Next'], inplace=True)
        
        X = df[features].values
        y_return = df['Target_Return_Next'].values
        prev_close_arr = df['Close'].values
        true_price_arr = df['Target_Close_Next'].values
        
        train_size = int(len(df) * 0.8)
        
        # ----------------------------------------------------
        # Part A: Train Machine Learning Models (XGBoost & LightGBM)
        # ----------------------------------------------------
        print("  Training XGBoost & LightGBM return models...")
        X_train_ml, X_test_ml = X[:train_size], X[train_size:]
        y_train_ml, y_test_ml = y_return[:train_size], y_return[train_size:]
        
        # XGBoost
        xgb_r = XGBRegressor(n_estimators=100, learning_rate=0.05, random_state=42)
        xgb_r.fit(X_train_ml, y_train_ml)
        joblib.dump(xgb_r, os.path.join(models_dir, f'xgb_return_{ticker}.pkl'))
        
        # LightGBM
        lgb_r = LGBMRegressor(n_estimators=100, learning_rate=0.05, random_state=42, verbose=-1)
        lgb_r.fit(X_train_ml, y_train_ml)
        joblib.dump(lgb_r, os.path.join(models_dir, f'lgb_return_{ticker}.pkl'))
        
        # ----------------------------------------------------
        # Part B: Train Deep Learning Models (LSTM & Transformer)
        # ----------------------------------------------------
        print("  Fitting StandardScaler and preparing DL sequences...")
        scaler = StandardScaler()
        # Scale train features and fit scaler
        X_train_scaled = scaler.fit_transform(X[:train_size])
        X_test_scaled = scaler.transform(X[train_size:])
        X_scaled = np.vstack((X_train_scaled, X_test_scaled))
        
        joblib.dump(scaler, os.path.join(models_dir, f'scaler_{ticker}.pkl'))
        
        # Generate sequential batches
        X_seq, y_seq, prev_seq, true_seq = create_sequences(X_scaled, y_return, prev_close_arr, true_price_arr, SEQ_LENGTH)
        
        # Train/Validation split on sequences
        seq_train_size = int(len(X_seq) * 0.8)
        X_train_seq, X_val_seq = X_seq[:seq_train_size], X_seq[seq_train_size:]
        y_train_seq, y_val_seq = y_seq[:seq_train_size], y_seq[seq_train_size:]
        
        train_dataset = TensorDataset(torch.tensor(X_train_seq, dtype=torch.float32), torch.tensor(y_train_seq, dtype=torch.float32))
        val_dataset = TensorDataset(torch.tensor(X_val_seq, dtype=torch.float32), torch.tensor(y_val_seq, dtype=torch.float32))
        
        train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True)
        val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False)
        
        input_size = len(features)
        
        # LSTM Model
        print("  Training LSTM Neural Network...")
        lstm_model = LSTMModel(input_size, HIDDEN_SIZE)
        criterion = nn.MSELoss()
        optimizer = torch.optim.Adam(lstm_model.parameters(), lr=LR)
        lstm_model = train_model(lstm_model, train_loader, val_loader, criterion, optimizer, EPOCHS, PATIENCE)
        torch.save(lstm_model.state_dict(), os.path.join(models_dir, f'lstm_{ticker}.pt'))
        
        # Transformer Model
        print("  Training Transformer Neural Network...")
        trans_model = TransformerModel(input_size, HIDDEN_SIZE)
        optimizer = torch.optim.Adam(trans_model.parameters(), lr=LR)
        trans_model = train_model(trans_model, train_loader, val_loader, criterion, optimizer, EPOCHS, PATIENCE)
        torch.save(trans_model.state_dict(), os.path.join(models_dir, f'transformer_{ticker}.pt'))
        
        print(f"  [Success] Saved all 4 model weight files for {ticker} into {models_dir}")

    print("\nAll PSX stock training pipelines executed successfully!")

if __name__ == "__main__":
    train_stock_pipelines()
