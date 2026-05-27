import time
from datetime import datetime
from pathlib import Path

import pandas as pd
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By


APP_URL = "https://www.rooktec.in/wmapp"
USERNAME = "smb"
PASSWORD = "wind@smb"

OUTPUT_FILE = "wind_turbine_data.xlsx"
INTERVAL_SECONDS = 15 * 60


def setup_driver():
    options = Options()
    options.add_argument("--start-maximized")

    driver = webdriver.Chrome(options=options)
    driver.set_page_load_timeout(60)
    return driver


def login(driver):
    driver.get(APP_URL)
    time.sleep(3)

    inputs = driver.find_elements(By.TAG_NAME, "input")

    username_inputs = [
        item
        for item in inputs
        if (item.get_attribute("type") or "").lower() in ["text", "email", ""]
    ]

    password_inputs = [
        item
        for item in inputs
        if (item.get_attribute("type") or "").lower() == "password"
    ]

    if not username_inputs or not password_inputs:
        raise RuntimeError("Login input fields not found.")

    username_inputs[0].clear()
    username_inputs[0].send_keys(USERNAME)

    password_inputs[0].clear()
    password_inputs[0].send_keys(PASSWORD)

    buttons = driver.find_elements(By.XPATH, "//button | //input[@type='submit']")

    if buttons:
        buttons[0].click()
    else:
        password_inputs[0].submit()

    time.sleep(6)


def collect_turbine_data(driver):
    time.sleep(3)

    records = driver.execute_script(
        """
        const cards = Array.from(
            document.querySelectorAll("div.runningdiv:not(.runningdivdimmed)")
        );

        function clean(value) {
            return (value || "")
                .replace(/\\u00a0/g, " ")
                .replace(/\\s+/g, " ")
                .trim();
        }

        function getDirectDivs(card) {
            return Array.from(card.children).filter(
                child => child.tagName.toLowerCase() === "div"
            );
        }

        function getName(card) {
            const divs = getDirectDivs(card);
            const nameP = divs[0]?.querySelector("p");
            return clean(nameP?.innerText || "");
        }

        function getStatus(card) {
            const divs = getDirectDivs(card);
            const statusDiv = divs[1];

            if (!statusDiv) return "";

            const statusP = statusDiv.querySelector("p");

            if (!statusP) return "";

            const title = clean(statusP.getAttribute("title") || "");
            const text = clean(statusP.innerText || "");

            return title || text;
        }

        function getTodayKwh(card) {
            const spans = Array.from(card.querySelectorAll("span"));

            for (const span of spans) {
                const parent = span.parentElement;

                if (!parent) continue;

                const display = window.getComputedStyle(parent).display;
                const parentText = clean(parent.innerText || "");

                if (
                    display !== "none" &&
                    parentText.includes("Today") &&
                    parentText.includes("Kwh")
                ) {
                    const value = clean(span.innerText || "");

                    if (/^\\d+(\\.\\d+)?$/.test(value)) {
                        return value;
                    }
                }
            }

            return "";
        }

        function getWsKw(card) {
            let windSpeed = "";
            let kw = "";

            const divs = Array.from(card.querySelectorAll("div"));

            for (const div of divs) {
                const text = clean(div.innerText || "");

                if (!windSpeed) {
                    const wsMatch = text.match(/Ws\\s*:\\s*([0-9.,]+)/i);

                    if (wsMatch) {
                        windSpeed = wsMatch[1].replace(",", ".");
                    }
                }

                if (!kw) {
                    const kwMatch = text.match(/Kw\\s*:\\s*([0-9.,]+)/i);

                    if (kwMatch) {
                        kw = kwMatch[1].replace(",", ".");
                    }
                }
            }

            return { windSpeed, kw };
        }

        function getDateTime(card) {
            const pTags = Array.from(card.querySelectorAll("p"));

            for (const p of pTags) {
                const text = clean(p.innerText || "");

                if (/^\\d{4}-\\d{2}-\\d{2},\\s*\\d{1,2}:\\d{2}$/.test(text)) {
                    return text.replace(" ", "");
                }
            }

            return "";
        }

        return cards.map((card, index) => {
            const wsKw = getWsKw(card);

            return {
                card_no: String(index + 1),
                turbine_name: getName(card),
                status: getStatus(card),
                today_kwh: getTodayKwh(card),
                wind_speed: wsKw.windSpeed,
                kw: wsKw.kw,
                turbine_datetime: getDateTime(card),
                raw_text: clean(card.innerText || "")
            };
        });
        """
    )

    collected_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    for record in records:
        record["collected_at"] = collected_at

    print(f"Collected {len(records)} configured windmill cards")
    return records


def save_to_excel(records):
    if not records:
        print("No turbine data found.")
        return

    columns = [
        "collected_at",
        "card_no",
        "turbine_name",
        "status",
        "today_kwh",
        "wind_speed",
        "kw",
        "turbine_datetime",
        "raw_text",
    ]

    new_df = pd.DataFrame(records).reindex(columns=columns)

    output_path = Path(OUTPUT_FILE)

    if output_path.exists():
        old_df = pd.read_excel(output_path, dtype=str)
        final_df = pd.concat([old_df, new_df], ignore_index=True)
    else:
        final_df = new_df

    final_df.to_excel(output_path, index=False)
    print(f"Saved {len(records)} records to {OUTPUT_FILE}")


def main():
    driver = setup_driver()

    try:
        login(driver)

        while True:
            print("Collecting wind turbine data...")
            records = collect_turbine_data(driver)
            save_to_excel(records)

            print("Waiting 15 minutes for next collection...")
            time.sleep(INTERVAL_SECONDS)

            driver.refresh()
            time.sleep(5)

    except KeyboardInterrupt:
        print("Stopped by user.")

    finally:
        driver.quit()


if __name__ == "__main__":
    main()