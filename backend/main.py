import FastAPI, Depends, Query, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
import json
from database import SessionLocal, Fund, FundNAV, MarketIndicator, NewsArticle
from recommender import get_recommendations

app = FastAPI(title="AI-Based Mutual Fund Advisor API")

# Configure CORS (Cross-Origin Resource Sharing)
# This allows our React frontend (running on a different port like http://localhost:5173)
# to make requests to our Python backend (running on http://localhost:8000)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], # In production, replace with specific frontend URLs
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Helper function to get database session per request
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

@app.get("/")
def read_root():
    return {"message": "Welcome to the AI-Based Mutual Fund Investment Advisor API!"}

@app.get("/api/market")
def get_market_summary(db: Session = Depends(get_db)):
    """
    Returns the latest macroeconomic indicators.
    """
    indicator = db.query(MarketIndicator).order_by(MarketIndicator.date.desc()).first()
    if not indicator:
        raise HTTPException(status_code=404, detail="No market indicators found.")
        
    return {
        "date": indicator.date,
        "kse100": indicator.kse100,
        "kibor_6m": indicator.kibor_6m,
        "inflation_rate": indicator.inflation_rate,
        "gold_price": indicator.gold_price,
        "exchange_rate_usd": indicator.exchange_rate_usd
    }

@app.get("/api/funds")
def get_all_funds(db: Session = Depends(get_db)):
    """
    Returns a list of all mutual funds in the database.
    """
    funds = db.query(Fund).all()
    return [{
        "id": f.id,
        "name": f.name,
        "category": f.category,
        "risk_level": f.risk_level,
        "is_islamic": f.is_islamic,
        "fund_size_mkr": round(f.fund_size_mkr, 1) if f.fund_size_mkr else None
    } for f in funds]

@app.get("/api/funds/{fund_id}")
def get_fund_detail(fund_id: int, db: Session = Depends(get_db)):
    """
    Returns details for a specific fund including its daily NAV history
    sorted chronologically (oldest to newest) to draw charts.
    """
    fund = db.query(Fund).filter(Fund.id == fund_id).first()
    if not fund:
        raise HTTPException(status_code=404, detail="Fund not found")
        
    # Get historical NAVs
    nav_history = db.query(FundNAV).filter(FundNAV.fund_id == fund_id).order_by(FundNAV.date.asc()).all()
    
    return {
        "id": fund.id,
        "name": fund.name,
        "category": fund.category,
        "risk_level": fund.risk_level,
        "is_islamic": fund.is_islamic,
        "fund_size_mkr": round(fund.fund_size_mkr, 1) if fund.fund_size_mkr else None,
        "nav_history": [{
            "date": nav.date,
            "nav": nav.nav
        } for nav in nav_history]
    }

@app.get("/api/recommend")
def get_personalized_recommendations(
    amount: float = Query(..., description="Investment amount in PKR"),
    risk: str = Query(..., description="Risk tolerance (Low, Medium, High)"),
    preference: str = Query(..., description="Preference (Islamic, Conventional, Any)"),
    db: Session = Depends(get_db)
):
    """
    Endpoint that takes user input and returns the top 3 recommended mutual funds.
    """
    if risk not in ["Low", "Medium", "High"]:
        raise HTTPException(status_code=400, detail="Invalid risk parameter. Must be Low, Medium, or High.")
    if preference not in ["Islamic", "Conventional", "Any"]:
        raise HTTPException(status_code=400, detail="Invalid preference. Must be Islamic, Conventional, or Any.")
        
    recommendations = get_recommendations(db, amount, risk, preference)
    return recommendations
Xm` 