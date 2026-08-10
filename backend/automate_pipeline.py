import time
import datetime
from news_pipeline import run_pipeline

# Set the interval (how often the scraper should run)
# For production/real use, we run it every 30 minutes (1800 seconds)
# For testing, we can run it every 15 seconds to see it work!
INTERVAL_SECONDS = 30 

def start_scheduler():
    print("====================================================")
    # This is standard terms for university presentations:
    print("PAKISTAN FINANCIAL NEWS INGESTION SCHEDULER ACTIVE")
    print(f"Checking feeds every {INTERVAL_SECONDS} seconds...")
    print("Press CTRL + C to stop the scheduler.")
    print("====================================================")

    try:
        while True:
            current_time = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            print(f"\n[{current_time}] Triggering news pipeline...")
            
            # Run the scraper, clean data, extract features, and store
            run_pipeline()
            
            print(f"Waiting for {INTERVAL_SECONDS} seconds before next check...")
            time.sleep(INTERVAL_SECONDS)
            
    except KeyboardInterrupt:
        print("\nScheduler stopped by user.")

if __name__ == "__main__":
    start_scheduler()