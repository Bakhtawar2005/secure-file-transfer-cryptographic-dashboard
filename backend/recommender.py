import json
import pandas as pd
import numpy as np
from sqlalchemy.orm import Session
from database import SessionLocal, UserProfile, Recommendation, Fund, MarketIndicator, NewsArticle
from model import predict_future_returns

def get_recommendations(db: Session, investment_amount: float, risk_tolerance: str, preference: str):
    """
    Ranks funds based on user preferences and AI-predicted returns.
    Saves the user profile and recommendations to the database.
    """
    # 1. Fetch latest predictions and features from our machine learning model
    df_funds = predict_future_returns(db)
    if df_funds.empty:
        print("No fund data available for recommendations.")
        return []
        
    # 2. Filter funds based on Islamic/Conventional preference
    if preference == "Islamic":
        df_filtered = df_funds[df_funds["fund_is_islamic"] == True]
    elif preference == "Conventional":
        df_filtered = df_funds[df_funds["fund_is_islamic"] == False]
    else: # "Any"
        df_filtered = df_funds.copy()
        
    if df_filtered.empty:
        return []
        
    # 3. Filter funds based on user's Risk Tolerance
    # Low tolerance -> Only Low-risk money market funds
    # Medium tolerance -> Low, Medium-Low, and Medium risk funds
    # High tolerance -> All funds allowed
    if risk_tolerance == "Low":
        df_filtered = df_filtered[df_filtered["fund_risk_level"] == "Low"]
    elif risk_tolerance == "Medium":
        df_filtered = df_filtered[df_filtered["fund_risk_level"].isin(["Low", "Medium-Low", "Medium"])]
    
    if df_filtered.empty:
        return []
        
    # 4. Fetch the latest market indicators and news sentiment for scoring
    latest_market = db.query(MarketIndicator).order_by(MarketIndicator.date.desc()).first()
    latest_news = db.query(NewsArticle).order_by(NewsArticle.published_at.desc()).all()
    
    # Calculate average recent news sentiment
    avg_sentiment = 0.0
    if latest_news:
        recent_sentiment = [n.sentiment_score for n in latest_news[:10]] # Avg of last 10 articles
        avg_sentiment = sum(recent_sentiment) / len(recent_sentiment)
        
    # Calculate KSE-100 stock market trend (positive or negative index return)
    # We retrieve the last 30 days of market records to see if the index is trending up
    market_records = db.query(MarketIndicator.kse100).order_by(MarketIndicator.date.desc()).limit(30).all()
    market_trend = 0.0
    if len(market_records) >= 2:
        kse_current = market_records[0][0]
        kse_past = market_records[-1][0]
        market_trend = (kse_current - kse_past) / kse_past if kse_past else 0.0
        
    # 5. Apply the Scoring Formula
    scored_funds = []
    
    # Normalize features between 0 and 1 across our filtered list to make scoring fair
    min_return = df_filtered["predicted_return_30d"].min()
    max_return = df_filtered["predicted_return_30d"].max()
    return_span = (max_return - min_return) if max_return != min_return else 1.0
    
    min_vol = df_filtered["volatility_30d"].min()
    max_vol = df_filtered["volatility_30d"].max()
    vol_span = (max_vol - min_vol) if max_vol != min_vol else 1.0
    
    for _, row in df_filtered.iterrows():
        # A. Predicted Return Score (35%)
        # Scale return between 0 and 1 (higher is better)
        return_score = (row["predicted_return_30d"] - min_return) / return_span
        
        # B. Historical Stability Score (20%)
        # Stability is the opposite of volatility (lower volatility is better)
        stability_score = 1.0 - ((row["volatility_30d"] - min_vol) / vol_span)
        
        # C. News Sentiment Score (15%)
        # Scale sentiment from (-1 to +1) to (0 to 1)
        sentiment_score = (avg_sentiment + 1.0) / 2.0
        
        # D. Market Trend Score (10%)
        # Stock market rally boosts equity funds; cuts interest rates boost income/debt funds
        category = row["fund_category"].lower()
        if "equity" in category:
            trend_score = max(0.0, min(1.0, (market_trend + 0.1) / 0.2)) # positive index trend boosts score
        elif "income" in category or "debt" in category:
            # Policy rate drops are positive for income funds (bond prices go up)
            kibor_rate = latest_market.kibor_6m if latest_market else 20.0
            trend_score = 1.0 - (kibor_rate / 30.0) # lower rate is better
        else: # Money market (always stable)
            trend_score = 0.8
            
        # E. Fund Size Score (5%)
        # We mock a small size factor (larger is slightly safer)
        size_score = 0.7
        
        # F. Risk suitability matching score (15%)
        # If the fund matches user's exact risk profile, give a boost
        risk_match_score = 1.0 if row["fund_risk_level"] == risk_tolerance else 0.5
        
        # Calculate Final Weighted Score
        final_score = (
            0.35 * return_score +
            0.20 * stability_score +
            0.15 * risk_match_score +
            0.15 * sentiment_score +
            0.10 * trend_score +
            0.05 * size_score
        )
        
        # Convert final score to a percentage (Confidence Score)
        confidence_percentage = round(final_score * 100, 1)
        
        # Generate personalized reasons (supporting insights) for the user
        reasons = []
        # Return reason
        reasons.append(f"AI forecasts a competitive return of {row['predicted_return_30d']*100:.2f}% over the next 30 days.")
        # Risk / Stability reason
        if row["fund_risk_level"] == "Low":
            reasons.append("Boasts high stability with extremely low volatility, matching your conservative profile.")
        else:
            reasons.append(f"Exhibits a moderate risk profile (volatility of {row['volatility_30d']*100:.1f}%) in exchange for higher returns.")
        # Sentiment/Market reason
        if avg_sentiment > 0.1:
            reasons.append("Recent economic news sentiment in Pakistan is positive, creating a favorable investment climate.")
        if "equity" in category and market_trend > 0.02:
            reasons.append("The KSE-100 index is trending upwards, giving a positive momentum boost to stock investments.")
        if row["fund_is_islamic"]:
            reasons.append("Operates under 100% Shariah-compliant asset allocations.")
            
        scored_funds.append({
            "fund_id": row["fund_id"],
            "name": row["fund_name"],
            "category": row["fund_category"],
            "risk_level": row["fund_risk_level"],
            "is_islamic": row["fund_is_islamic"],
            "predicted_return": round(row["predicted_return_30d"] * 100, 3), # as a percentage
            "confidence_score": confidence_percentage,
            "reasons": reasons
        })
        
    # Sort funds by final score in descending order (highest score first)
    scored_funds = sorted(scored_funds, key=lambda x: x["confidence_score"], reverse=True)
    
    # Take the top 3 recommendations
    top_recommendations = scored_funds[:3]
    
    # 6. Save the user profile query to database
    user_profile = UserProfile(
        investment_amount=investment_amount,
        risk_tolerance=risk_tolerance,
        horizon_months=12, # default horizon
        preference=preference
    )
    db.add(user_profile)
    db.commit()
    db.refresh(user_profile)
    
    # Save the recommendations records to database linked to the user profile
    for rank, rec in enumerate(top_recommendations, 1):
        db_rec = Recommendation(
            user_profile_id=user_profile.id,
            fund_id=rec["fund_id"],
            rank=rank,
            expected_return=rec["predicted_return"],
            risk_level=rec["risk_level"],
            confidence_score=rec["confidence_score"],
            reasons=json.dumps(rec["reasons"])
        )
        db.add(db_rec)
    db.commit()
    
    return top_recommendations

if __name__ == "__main__":
    db = SessionLocal()
    try:
        print("Testing Recommendation Engine for a user:")
        print("Inputs: Amount=100k, Risk=Low, Preference=Islamic")
        recs = get_recommendations(db, 100000.0, "Low", "Islamic")
        
        for r in recs:
            print(f"\nRank {r['confidence_score']}% Confidence: {r['name']} ({r['category']})")
            print(f" -> Predicted 30-Day Return: {r['predicted_return']}%")
            print(" -> Supporting Reasons:")
            for reason in r["reasons"]:
                print(f"    * {reason}")
    finally:
        db.close()