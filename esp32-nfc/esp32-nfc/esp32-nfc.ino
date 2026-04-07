#include <SPI.h>
#include <MFRC522.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <time.h>

// -------- WiFi / Firebase --------
const char* ssid = "Tommy kahla";
const char* password = "tommy2003";
const char* firebaseURL = "https://nfc-lab-access-app-default-rtdb.firebaseio.com/";

// -------- RFID Pins --------
#define SS_PIN     5
#define RST_PIN    22

// -------- IR Sensor Pins --------
#define IR_PINA 32
#define IR_PINB 33

// -------- Output Pins --------
#define LED_GREEN 26
#define LED_RED   14
#define BUZZER_PIN 4

// -------- RFID --------
MFRC522 mfrc522(SS_PIN, RST_PIN);

// -------- People Counter --------
int peopleCount = 0;
char firstBeam = 0;
unsigned long beamTimer = 0;
const unsigned long beamTimeout = 400;

const unsigned long updateInterval = 10000;
unsigned long lastUpdate = 0;

#define MAX_UIDS 100

String authorizedUIDs[MAX_UIDS];
int uidCount = 0;

int lastUIDRefreshDay = -1;
const int uidRefreshHour = 23;   // 11 PM
const int uidRefreshMinute = 59; // 11:59 PM

// ================== SETUP ==================
void setup() {
  Serial.begin(115200);

  pinMode(IR_PINA, INPUT_PULLUP);
  pinMode(IR_PINB, INPUT_PULLUP);

  pinMode(LED_GREEN, OUTPUT);
  pinMode(LED_RED, OUTPUT);

  // WiFi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\nWiFi connected!");

  // Time
  configTime(0, 0, "pool.ntp.org");
  setenv("TZ", "EST5EDT,M3.2.0/2,M11.1.0/2", 1);
  tzset();

  while (time(nullptr) < 100000) {
    delay(300);
    Serial.print(".");
  }

  Serial.println("\nTime synced!");

  // RFID
  SPI.begin(18, 19, 23, 5);
  mfrc522.PCD_Init();

  // Buzzer
  ledcAttach(BUZZER_PIN, 2000, 8);

  fetchAuthorizedUIDs();

  Serial.println("System Ready!");
}

// ================== LOOP ==================
void loop() {
  handleRFID();
  handlePeopleCounter();
  refreshUIDsAtNight();

  // Periodic Firebase update
  if (millis() - lastUpdate >= updateInterval) {
    lastUpdate = millis();
    updateCounterToFirebase();
  }
}

// ================== RFID ==================
void handleRFID() {
  if (!mfrc522.PICC_IsNewCardPresent()) return;
  if (!mfrc522.PICC_ReadCardSerial()) return;

  String uidString = "";

  for (byte i = 0; i < mfrc522.uid.size; i++) {
    if (mfrc522.uid.uidByte[i] < 0x10) uidString += "0";
    uidString += String(mfrc522.uid.uidByte[i], HEX);
  }

  uidString.toUpperCase();

  Serial.print("UID: ");
  Serial.println(uidString);

  bool allowed = checkUIDLocal(uidString);

  if (allowed) {
    accessGranted();
    logAccess(uidString, "allow");
  } else {
    accessDenied();
    logAccess(uidString, "deny");
  }

  mfrc522.PICC_HaltA();
  mfrc522.PCD_StopCrypto1();
}

// ================== PEOPLE COUNTER ==================
void handlePeopleCounter() {
  bool a = digitalRead(IR_PINA);
  bool b = digitalRead(IR_PINB);

  if (firstBeam == 0) {
    if (a == LOW) {
      firstBeam = 'A';
      beamTimer = millis();
    } else if (b == LOW) {
      firstBeam = 'B';
      beamTimer = millis();
    }
  }

  if (firstBeam == 'A' && b == LOW) {
    peopleCount++;
    Serial.print("ENTER: ");
    Serial.println(peopleCount);
    waitForClear();
    firstBeam = 0;
  }

  if (firstBeam == 'B' && a == LOW) {
    peopleCount--;
    if (peopleCount < 0) peopleCount = 0;
    Serial.print("EXIT: ");
    Serial.println(peopleCount);
    waitForClear();
    firstBeam = 0;
  }

  if (firstBeam != 0 && millis() - beamTimer > beamTimeout) {
    firstBeam = 0;
  }
}

// ================== ACCESS FEEDBACK ==================
void accessGranted() {
  Serial.println("Access Granted");
  digitalWrite(LED_GREEN, HIGH);
  digitalWrite(LED_RED, LOW);
  ledcWriteTone(BUZZER_PIN, 2000);
  delay(120);
  ledcWriteTone(BUZZER_PIN, 2500);
  delay(120);
  ledcWriteTone(BUZZER_PIN, 0);
  delay(500);
  digitalWrite(LED_GREEN, LOW);
}

void accessDenied() {
  Serial.println("Access Denied");
  digitalWrite(LED_RED, HIGH);
  digitalWrite(LED_GREEN, LOW);
  ledcWriteTone(BUZZER_PIN, 400);
  delay(600);
  ledcWriteTone(BUZZER_PIN, 0);
  delay(500);
  digitalWrite(LED_RED, LOW);
}

// ================== FIREBASE ==================
bool checkUIDInFirebase(String uid) {
  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;

  String url = String(firebaseURL) + "authorized_uids/" + uid + "/Access.json";

  http.begin(client, url);
  int code = http.GET();

  if (code > 0) {
    String payload = http.getString();
    http.end();
    return (payload == "true");
  }

  http.end();
  return false;
}

void logAccess(String uid, String decision) {
  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;

  time_t now = time(nullptr);
  struct tm *timeinfo = localtime(&now);

  char buffer[30];
  strftime(buffer, sizeof(buffer), "%Y-%m-%dT%H:%M:%S", timeinfo);

  String json = "{";
  json += "\"uid\":\"" + uid + "\",";
  json += "\"decision\":\"" + decision + "\",";
  json += "\"count\":" + String(peopleCount) + ",";
  json += "\"timestamp\":\"" + String(buffer) + "\"}";
  
  http.begin(client, String(firebaseURL) + "access_logs.json");
  http.addHeader("Content-Type", "application/json");
  http.POST(json);
  http.end();
}

void updateCounterToFirebase() {
  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;

  http.begin(client, String(firebaseURL) + "occupancy/lab-101/current_count.json");
  http.PUT(String(peopleCount));
  http.end();
}
void refreshUIDsAtNight() {
  time_t now = time(nullptr);
  struct tm *timeinfo = localtime(&now);

  if (timeinfo->tm_hour == uidRefreshHour &&
      timeinfo->tm_min == uidRefreshMinute &&
      timeinfo->tm_mday != lastUIDRefreshDay) {

    Serial.println("Nightly UID refresh started...");
    fetchAuthorizedUIDs();
    lastUIDRefreshDay = timeinfo->tm_mday;
  }
}
void fetchAuthorizedUIDs() {
  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;

  String url = String(firebaseURL) + "authorized_uids.json";
  http.begin(client, url);

  int code = http.GET();

  if (code > 0) {
    String payload = http.getString();
    Serial.println("Authorized UID data:");
    Serial.println(payload);

    uidCount = 0;
    int searchStart = 0;

    while (true) {
      int keyStart = payload.indexOf('\"', searchStart);
      if (keyStart == -1) break;

      int keyEnd = payload.indexOf('\"', keyStart + 1);
      if (keyEnd == -1) break;

      String uid = payload.substring(keyStart + 1, keyEnd);

      int accessPos = payload.indexOf("\"Access\":true", keyEnd);

      if (accessPos != -1 && accessPos < keyEnd + 200) {
        bool alreadyStored = false;

        for (int i = 0; i < uidCount; i++) {
          if (authorizedUIDs[i] == uid) {
            alreadyStored = true;
            break;
          }
        }

        if (!alreadyStored && uidCount < MAX_UIDS) {
          uid.toUpperCase();
          authorizedUIDs[uidCount] = uid;
          uidCount++;

          Serial.print("Stored UID: ");
          Serial.println(uid);
        }
      }

      searchStart = keyEnd + 1;
    }

    Serial.print("Total stored UIDs: ");
    Serial.println(uidCount);
  } else {
    Serial.print("Failed to fetch UIDs. HTTP code: ");
    Serial.println(code);
  }

  http.end();
}
bool checkUIDLocal(String uid) {
  for (int i = 0; i < uidCount; i++) {
    if (authorizedUIDs[i] == uid) {
      return true;
    }
  }
  return false;
}
// ================== HELPER ==================
void waitForClear() {
  while (digitalRead(IR_PINA) == LOW || digitalRead(IR_PINB) == LOW) {
    delay(5);
  }
}
