import datetime
from sqlalchemy import create_engine, Column, Integer, String, Float, Boolean, Date, DateTime, ForeignKey
from sqlalchemy.orm import declarative_base, sessionmaker, relationship

# We save all database data in a local file named 'advisor.db' inside the backend folder
DATABASE_URL = "sqlite:///./advisor.db"

engine = create_engine(
    DATABASE_URL, connect_args={"check_same_thread": False}
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()

class Fund(Base):
    """
    Represents a Mutual Fund.
    """
    __tablename__ = "funds"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, unique=True, index=True, nullable=False)
    category = Column(String, index=True, nullable=False) # e.g. Equity, Income, Money Market
    risk_level = Column(String, nullable=False)           # Low, Medium, High
    is_islamic = Column(Boolean, default=False)           # Islamic vs Conventional
    fund_size_mkr = Column(Float, nullable=True)          # Fund Size in Million PKR
    launch_date = Column(Date, nullable=True)

    # Relationships
    nav_history = relationship("FundNAV", back_populates="fund", cascade="all, delete-orphan")
    recommendations = relationship("Recommendation", back_populates="fund", cascade="all, delete-orphan")

class FundNAV(Base):
    """
    Represents Net Asset Value (NAV) price of a Mutual Fund on a specific date.
    """
    __tablename__ = "fund_navs"

    id = Column(Integer, primary_key=True, index=True)
    fund_id = Column(Integer, ForeignKey("funds.id"), nullable=False)
    date = Column(Date, nullable=False, index=True)
    nav = Column(Float, nullable=False)

    # Relationship
    fund = relationship("Fund", back_populates="nav_history")

class MarketIndicator(Base):
    """
    Represents macroeconomic and market indicators (KSE-100, KIBOR interest rate, inflation, exchange rates).
    """
    __tablename__ = "market_indicators"

    id = Column(Integer, primary_key=True, index=True)
    date = Column(Date, unique=True, nullable=False, index=True)
    kse100 = Column(Float, nullable=True)             # KSE-100 Index value
    kibor_6m = Column(Float, nullable=True)           # 6-Month KIBOR policy rate (%)
    inflation_rate = Column(Float, nullable=True)     # CPI Inflation Rate (%)
    gold_price = Column(Float, nullable=True)         # Gold Price (PKR per tola)
    exchange_rate_usd = Column(Float, nullable=True)  # USD to PKR Exchange Rate

class NewsArticle(Base):
    """
    Represents collected financial news articles.
    """
    __tablename__ = "news_articles"

    id = Column(Integer, primary_key=True, index=True)
    source = Column(String, nullable=False)
    source_type = Column(String, nullable=False)               # RSS Feed, Web Scraping, API
    url = Column(String, unique=True, nullable=False, index=True)
    title = Column(String, nullable=False)
    description = Column(String, nullable=True)
    full_text = Column(String, nullable=True)
    published_time = Column(DateTime, nullable=False, index=True) # Published Time (UTC)
    ingestion_time = Column(DateTime, nullable=False, default=datetime.datetime.utcnow) # Ingestion Time (UTC)
    country = Column(String, nullable=False)
    region = Column(String, nullable=False)
    language = Column(String, nullable=False)
    asset_class = Column(String, nullable=False)
    market = Column(String, nullable=False)
    sector = Column(String, nullable=False)
    event_type = Column(String, nullable=False)
    importance_score = Column(Float, nullable=False)
    sentiment_score = Column(Float, default=0.0)
    confidence_score = Column(Float, nullable=False)
    keywords = Column(String, nullable=True)
    named_entities = Column(String, nullable=True)
    related_assets = Column(String, nullable=True)
    raw_response = Column(String, nullable=True)               # For debugging

    # Backward compatibility fields (so existing files do not break)
    content = Column(String, nullable=True)
    published_at = Column(DateTime, nullable=True, index=True)

class UserProfile(Base):
    """
    Represents a user's inputs to request personalized recommendations.
    """
    __tablename__ = "user_profiles"

    id = Column(Integer, primary_key=True, index=True)
    investment_amount = Column(Float, nullable=False)
    risk_tolerance = Column(String, nullable=False)    # Low, Medium, High
    horizon_months = Column(Integer, nullable=False)   # Investment duration in months
    preference = Column(String, nullable=False)        # Islamic, Conventional, Any
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    # Relationships
    recommendations = relationship("Recommendation", back_populates="user_profile", cascade="all, delete-orphan")

class Recommendation(Base):
    """
    Represents a recommendation record generated for a specific user profile query.
    """
    __tablename__ = "recommendations"

    id = Column(Integer, primary_key=True, index=True)
    user_profile_id = Column(Integer, ForeignKey("user_profiles.id"), nullable=False)
    fund_id = Column(Integer, ForeignKey("funds.id"), nullable=False)
    rank = Column(Integer, nullable=False)             # Ranked position (1, 2, 3, etc.)
    expected_return = Column(Float, nullable=False)    # Predicted return rate (%)
    risk_level = Column(String, nullable=False)        # Fund risk level
    confidence_score = Column(Float, nullable=False)   # Recommendation confidence percentage (0 to 100)
    reasons = Column(String, nullable=False)           # Text reasons (bullet points JSON)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    # Relationships
    user_profile = relationship("UserProfile", back_populates="recommendations")
    fund = relationship("Fund", back_populates="recommendations")


class StockPrice(Base):
    """
    Represents historical daily OHLCV stock prices and calculated technical indicators.
    """
    __tablename__ = "stock_prices"

    id = Column(Integer, primary_key=True, index=True)
    ticker = Column(String, index=True, nullable=False)
    company_name = Column(String, nullable=False)
    date = Column(Date, index=True, nullable=False)
    open = Column(Float, nullable=False)
    high = Column(Float, nullable=False)
    low = Column(Float, nullable=False)
    close = Column(Float, nullable=False)
    volume = Column(Integer, nullable=False)

    # Technical Indicators and Metrics
    sma_20 = Column(Float, nullable=True)
    rsi_14 = Column(Float, nullable=True)
    macd = Column(Float, nullable=True)
    macd_signal = Column(Float, nullable=True)
    macd_hist = Column(Float, nullable=True)
    bb_high = Column(Float, nullable=True)
    bb_low = Column(Float, nullable=True)
    obv = Column(Float, nullable=True)
    atr_14 = Column(Float, nullable=True)
    ema_50 = Column(Float, nullable=True)
    daily_return = Column(Float, nullable=True)
    high_low_spread = Column(Float, nullable=True)
    rolling_volatility_30d = Column(Float, nullable=True) # Risk/Volatility metric


def init_db():
    """
    Helper function to initialize the database and create all tables.
    """
    Base.metadata.create_all(bind=engine)
    print("Database tables created successfully!")

if __name__ == "__main__":
    init_db()