import datetime
import random
import requests
from bs4 import BeautifulSoup
from sqlalchemy.orm import Session
from database import SessionLocal, Fund, FundNAV, init_db

# Headers to make the scraper look like a real browser
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}

def parse_float(value_str):
    """
    Safely parse float numbers from string, removing commas and handling empty values.
    """
    if not value_str or value_str.strip() == "-" or value_str.strip() == "":
        return None
    try:
        return float(value_str.replace(",", "").strip())
    except ValueError:
        return None

def parse_date(date_str):
    """
    Safely parse dates from string (e.g., '22-Jul-2026', '2026-07-22').
    """
    if not date_str:
        return datetime.date.today()
    for fmt in ("%d-%b-%y", "%d-%b-%Y", "%Y-%m-%d", "%m/%d/%Y"):
        try:
            return datetime.datetime.strptime(date_str.strip(), fmt).date()
        except ValueError:
            continue
    return datetime.date.today()

def get_risk_level_by_category(category_name):
    """
    Determine risk level based on the fund category.
    """
    cat = category_name.lower()
    if "money market" in cat or "cash" in cat or "treasury" in cat:
        return "Low"
    elif "income" in cat or "debt" in cat or "sovereign" in cat:
        return "Medium-Low"
    elif "balanced" in cat or "asset allocation" in cat:
        return "Medium"
    elif "equity" in cat or "index" in cat or "stock" in cat:
        return "High"
    return "Medium"

def is_islamic_fund(fund_name, category_name):
    """
    Determine if a fund is Islamic based on keywords in its name or category.
    """
    keywords = ["islamic", "shariah", "meezan", "al-ameen", "comply", "compliant", "halal"]
    text = (fund_name + " " + category_name).lower()
    return any(kw in text for kw in keywords)

def scrape_mufap_realtime(db: Session):
    """
    Scrapes the official MUFAP website for daily NAV announcements.
    """
    url = "https://www.mufap.com.pk/nav-announcement.php"
    print(f"Connecting to MUFAP at {url}...")
    
    try:
        response = requests.get(url, headers=HEADERS, timeout=15)
        if response.status_code != 200:
            print(f"Failed to fetch page. Status code: {response.status_code}")
            return False
            
        soup = BeautifulSoup(response.content, "html.parser")
        
        # MUFAP tables are usually nested inside a div or form. Let's find tables.
        tables = soup.find_all("table")
        if not tables:
            print("No tables found on the MUFAP page.")
            return False
            
        # Typically the main table is the largest table or has rows with multiple cells
        main_table = None
        for table in tables:
            rows = table.find_all("tr")
            if len(rows) > 30: # Our daily announcement table has a lot of rows
                main_table = table
                break
                
        if not main_table:
            # Fallback to the first table if none has > 30 rows
            main_table = tables[0]
            
        rows = main_table.find_all("tr")
        print(f"Found table with {len(rows)} rows. Parsing data...")
        
        current_category = "Conventional Money Market"
        funds_count = 0
        navs_count = 0
        
        for row in rows:
            cells = row.find_all(["td", "th"])
            if not cells:
                continue
                
            # If the row has 1 column and spans across, it's a Category header
            if len(cells) == 1:
                category_text = cells[0].text.strip()
                if category_text and len(category_text) > 3 and not "mutual fund" in category_text.lower():
                    current_category = category_text
                continue
                
            # If it's a header row with column labels, skip it
            row_text = "".join([c.text.lower() for c in cells])
            if "fund name" in row_text or "nav" not in row_text:
                continue
                
            # Parse regular data row
            # Columns usually: [0] Fund Name, [1] Class/Type, [2] NAV, [3] Offer, [4] Repurchase, [5] Date
            if len(cells) >= 4:
                fund_name = cells[0].text.strip()
                nav_str = cells[2].text.strip()
                date_str = cells[-1].text.strip() if len(cells) >= 5 else ""
                
                nav_val = parse_float(nav_str)
                if not fund_name or nav_val is None:
                    continue
                    
                nav_date = parse_date(date_str)
                is_islamic = is_islamic_fund(fund_name, current_category)
                risk_lvl = get_risk_level_by_category(current_category)
                
                # Check if fund exists, otherwise create it
                fund = db.query(Fund).filter(Fund.name == fund_name).first()
                if not fund:
                    fund = Fund(
                        name=fund_name,
                        category=current_category,
                        risk_level=risk_lvl,
                        is_islamic=is_islamic,
                        fund_size_mkr=random.uniform(500, 15000) # Mock fund size for ranking
                    )
                    db.add(fund)
                    db.commit()
                    db.refresh(fund)
                    funds_count += 1
                
                # Check if NAV for this date already exists to prevent duplicates
                existing_nav = db.query(FundNAV).filter(
                    FundNAV.fund_id == fund.id,
                    FundNAV.date == nav_date
                ).first()
                
                if not existing_nav:
                    new_nav = FundNAV(
                        fund_id=fund.id,
                        date=nav_date,
                        nav=nav_val
                    )
                    db.add(new_nav)
                    navs_count += 1
                    
        db.commit()
        print(f"Scrape completed: Added {funds_count} new funds, {navs_count} daily NAV records.")
        return navs_count > 0
        
    except Exception as e:
        print(f"Error during MUFAP scraping: {e}")
        return False

def seed_fallback_data(db: Session):
    """
    Seeds the database with high-quality mock historical data for 15 Pakistani mutual funds.
    This guarantees the app always has data, even without internet or if scraping fails.
    """
    print("Scraper offline or MUFAP down. Seeding historical fallback data...")
    
    mock_funds = [
        # Money Market Funds (Low Risk)
        {"name": "Meezan Cash Fund", "category": "Islamic Money Market", "risk": "Low", "islamic": True, "base_nav": 50.0},
        {"name": "Al-Ameen Islamic Cash Fund", "category": "Islamic Money Market", "risk": "Low", "islamic": True, "base_nav": 100.0},
        {"name": "NBP Money Market Fund", "category": "Conventional Money Market", "risk": "Low", "islamic": False, "base_nav": 10.0},
        {"name": "MCB Cash Management Optimizer", "category": "Conventional Money Market", "risk": "Low", "islamic": False, "base_nav": 100.0},
        
        # Income Funds (Medium-Low Risk)
        {"name": "Meezan Sovereign Fund", "category": "Islamic Income", "risk": "Medium-Low", "islamic": True, "base_nav": 52.0},
        {"name": "NBP Islamic Income Fund", "category": "Islamic Income", "risk": "Medium-Low", "islamic": True, "base_nav": 10.5},
        {"name": "HBL Income Fund", "category": "Conventional Income", "risk": "Medium-Low", "islamic": False, "base_nav": 105.0},
        {"name": "Faysal Financial Sector Option", "category": "Conventional Income", "risk": "Medium-Low", "islamic": False, "base_nav": 100.0},
        
        # Balanced / Asset Allocation (Medium Risk)
        {"name": "Meezan Balanced Fund", "category": "Islamic Balanced", "risk": "Medium", "islamic": True, "base_nav": 15.0},
        {"name": "NBP Balanced Fund", "category": "Conventional Balanced", "risk": "Medium", "islamic": False, "base_nav": 20.0},
        
        # Equity Funds (High Risk)
        {"name": "Meezan Islamic Fund", "category": "Islamic Equity", "risk": "High", "islamic": True, "base_nav": 65.0},
        {"name": "Al-Ameen Shariah Stock Fund", "category": "Islamic Equity", "risk": "High", "islamic": True, "base_nav": 140.0},
        {"name": "NBP Stock Fund", "category": "Conventional Equity", "risk": "High", "islamic": False, "base_nav": 18.0},
        {"name": "HBL Growth Fund", "category": "Conventional Equity", "risk": "High", "islamic": False, "base_nav": 25.0},
        {"name": "MCB Pakistan Stock Market Fund", "category": "Conventional Equity", "risk": "High", "islamic": False, "base_nav": 95.0}
    ]
    
    # Generate 90 days of daily data ending today
    today = datetime.date.today()
    start_date = today - datetime.timedelta(days=90)
    
    funds_added = 0
    navs_added = 0
    
    for f_info in mock_funds:
        fund = db.query(Fund).filter(Fund.name == f_info["name"]).first()
        if not fund:
            fund = Fund(
                name=f_info["name"],
                category=f_info["category"],
                risk_level=f_info["risk"],
                is_islamic=f_info["islamic"],
                fund_size_mkr=random.uniform(1000, 20000)
            )
            db.add(fund)
            db.commit()
            db.refresh(fund)
            funds_added += 1
            
        # Now create 90 days of NAV data
        # We will simulate a random walk with category-specific trends
        current_nav = f_info["base_nav"]
        
        # Categories have different average daily returns and volatility:
        # Equity: higher daily return but higher volatility
        # Money Market: low daily return, extremely low volatility (always positive growth)
        if "equity" in f_info["category"].lower():
            daily_trend = 0.0005 # Upward market trend
            volatility = 0.008   # High volatility
        elif "income" in f_info["category"].lower() or "balanced" in f_info["category"].lower():
            daily_trend = 0.0003
            volatility = 0.003
        else: # Money Market
            daily_trend = 0.00025
            volatility = 0.0002  # Very stable growth
            
        for i in range(91):
            date_val = start_date + datetime.timedelta(days=i)
            # Skip weekends (standard mutual fund prices are published on business days)
            if date_val.weekday() >= 5:
                continue
                
            # Check if NAV already exists
            existing_nav = db.query(FundNAV).filter(
                FundNAV.fund_id == fund.id,
                FundNAV.date == date_val
            ).first()
            
            if not existing_nav:
                # Random walk return calculation
                daily_return = daily_trend + random.normalvariate(0, volatility)
                # Money market should rarely go down
                if "money market" in f_info["category"].lower():
                    daily_return = max(0.0001, daily_return)
                    
                current_nav = current_nav * (1.0 + daily_return)
                
                new_nav = FundNAV(
                    fund_id=fund.id,
                    date=date_val,
                    nav=round(current_nav, 4)
                )
                db.add(new_nav)
                navs_added += 1
                
    db.commit()
    print(f"Fallback database seeding completed: Created {funds_added} funds, {navs_added} historical NAV entries.")

def run_scraper():
    """
    Main entry point for daily scraping process.
    """
    db = SessionLocal()
    try:
        # First initialize the DB tables if not already present
        init_db()
        
        # Attempt to scrape real-time data
        success = scrape_mufap_realtime(db)
        
        # If scraper fails (offline or MUFAP changes layout), use fallback seeder
        if not success:
            seed_fallback_data(db)
            
    finally:
        db.close()

if __name__ == "__main__":
    run_scraper() 