# This python script exctracts the data from the official encs lab calendar
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import pandas as pd
import time
import re
from urllib.parse import urlparse, parse_qs


options = webdriver.ChromeOptions()
options.add_experimental_option("detach", True)
driver = webdriver.Chrome(options=options)
wait = WebDriverWait(driver, 15)

weekly_url = "https://calendar.encs.concordia.ca/view_v.php?id=72&date=20260413"
driver.get(weekly_url)

input("Log in completely, then press Enter here... ")

# Re-navigate after login so the calendar loads authenticated
driver.get(weekly_url)
wait.until(EC.presence_of_all_elements_located((By.TAG_NAME, "a")))
time.sleep(2)


MONTHS_FR = {
    "janvier": "01", "février": "02", "fevrier": "02",
    "mars": "03",    "avril": "04",   "mai": "05",
    "juin": "06",    "juillet": "07", "août": "08",
    "aout": "08",    "septembre": "09","octobre": "10",
    "novembre": "11","décembre": "12", "decembre": "12"
}


entries = driver.find_elements(By.TAG_NAME, "a")

links = []  # list of (href, start_time_from_text)
for e in entries:
    try:
        text = e.text.strip()
        href = e.get_attribute("href")
        if "Not available" in text and href and "view_entry.php" in href:
            # grab start time directly from link text
            m_start = re.match(r"(\d{1,2}:\d{2})", text)
            start_from_text = m_start.group(1) if m_start else None
            links.append((href, start_from_text))
    except Exception:
        continue

print(f"Total 'Not available' entry links found: {len(links)}")

# extract data from each detail page
rows = []

for i, (link, start_from_text) in enumerate(links, start=1):
    print(f"Opening {i}/{len(links)}: {link}")
    try:
        driver.get(link)
        wait.until(EC.presence_of_element_located((By.TAG_NAME, "body")))
        time.sleep(0.5)

        body_text = driver.find_element(By.TAG_NAME, "body").text
        clean = " ".join(body_text.split())


        date = None
        m_date = re.search(
            r"Date:\s+\w+,?\s+(\w+)\s+(\d{1,2}),?\s+(\d{4})",
            clean, re.IGNORECASE
        )
        if m_date:
            month_str = m_date.group(1).lower()
            day       = m_date.group(2).zfill(2)
            year      = m_date.group(3)
            month_num = MONTHS_FR.get(month_str)
            if month_num:
                date = f"{year}-{month_num}-{day}"


        if not date:
            qs = parse_qs(urlparse(link).query)
            raw_date = qs.get("date", [None])[0]
            if raw_date and len(raw_date) == 8 and raw_date.isdigit():
                date = f"{raw_date[0:4]}-{raw_date[4:6]}-{raw_date[6:8]}"


        qs = parse_qs(urlparse(link).query)
        room = None
        raw_user = qs.get("user", [None])[0] or ""
        m_room = re.search(r"[Hh][-_]?(\d{3,4})", raw_user)
        if m_room:
            room = f"H-{m_room.group(1)}"


        start_time = None
        end_time   = None
        m_time = re.search(
            r"Heure:\s*(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})",
            clean
        )
        if m_time:
            start_time = m_time.group(1)
            end_time   = m_time.group(2)
        else:

            start_time = start_from_text

        rows.append({
            "date":       date,
            "room":       room,
            "start_time": start_time,
            "end_time":   end_time,
        })

        print(f"  ✓ date={date}  room={room}  start={start_time}  end={end_time}")

    except Exception as ex:
        print(f"  ⚠️  Error on {link}: {ex}")
        rows.append({
            "date": None, "room": None,
            "start_time": None, "end_time": None,
        })

# build dataframe
df_raw = pd.DataFrame(rows)
df_raw.to_excel("lab_schedule_debug.xlsx", index=False)
print("\nDebug file saved: lab_schedule_debug.xlsx")

# Drop rows missing any key field
df = df_raw.dropna(subset=["date", "room", "start_time", "end_time"]).copy()

# Sort by date, room, start time
df["_date_sort"]  = pd.to_datetime(df["date"],       format="%Y-%m-%d", errors="coerce")
df["_start_sort"] = pd.to_datetime(df["start_time"], format="%H:%M",    errors="coerce")
df = df.sort_values(by=["_date_sort", "room", "_start_sort"])

df = df[["date", "room", "start_time", "end_time"]]
df.to_excel("lab_schedule_ready.xlsx", index=False)

print(f"Done — saved to lab_schedule_ready.xlsx")
print(f"Rows saved: {len(df)}  (of {len(df_raw)} total scraped)")
