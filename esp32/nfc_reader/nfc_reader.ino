#include <SPI.h>
#include <MFRC522.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <time.h>

// -------- WiFi / Firebase --------
const char* ssid = "samin";
const char* password = "password123";
const char* firebaseURL = "https://nfc-lab-access-app-default-rtdb.firebaseio.com/";

// -------- Pins --------
#define SS_PIN     5
#define RST_PIN    22

#define LED_GREEN  26
#define LED_RED    14
#define BUZZER_PIN 16

// -------- RFID --------
MFRC522 mfrc522(SS_PIN, RST_PIN);

// Access is now controlled by Firebase

void setup() {
  Serial.begin(115200);
  delay(1000);

  pinMode(LED_GREEN, OUTPUT);
  pinMode(LED_RED, OUTPUT);

  digitalWrite(LED_GREEN, LOW);
  digitalWrite(LED_RED, LOW);

  // Connect WiFi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println();
  Serial.println("WiFi connected!");

  // -------- TIME (AUTO DST FIX) --------
  configTime(0, 0, "pool.ntp.org");
  setenv("TZ", "EST5EDT,M3.2.0/2,M11.1.0/2", 1);
  tzset();

  Serial.println("Time syncing...");

  time_t now = time(nullptr);
  while (now < 100000) {
    delay(500);
    Serial.print(".");
    now = time(nullptr);
  }

  Serial.println();
  Serial.println("Time synced!");

  // Start SPI + RFID
  SPI.begin(18, 19, 23, 5);
  mfrc522.PCD_Init();

  // Buzzer
  ledcAttach(BUZZER_PIN, 2000, 8);

  Serial.println("Scan Card...");
}

void loop() {

  if (!mfrc522.PICC_IsNewCardPresent()) return;
  if (!mfrc522.PICC_ReadCardSerial()) return;

  String uidString = "";

  Serial.print("Card UID: ");
  for (byte i = 0; i < mfrc522.uid.size; i++) {
    if (mfrc522.uid.uidByte[i] < 0x10) {
      uidString += "0";
    }

    uidString += String(mfrc522.uid.uidByte[i], HEX);

    if (i < mfrc522.uid.size - 1) {
      uidString += " ";
    }

    Serial.print(mfrc522.uid.uidByte[i], HEX);
    Serial.print(" ");
  }
  Serial.println();

  uidString.toUpperCase();

  Serial.print("UID String: ");
  Serial.println(uidString);

  // Send UID to Firebase
  sendUIDToFirebase(uidString);

  // Check access from Firebase
  bool allowed = checkUIDInFirebase(uidString);

  if (allowed) {
    accessGranted();
    logAccessToFirebase(uidString, "allow");
  } else {
    accessDenied();
    logAccessToFirebase(uidString, "deny");
  }

  mfrc522.PICC_HaltA();
  mfrc522.PCD_StopCrypto1();

  delay(400);
}

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

void sendUIDToFirebase(String uid) {
  WiFiClientSecure client;
  client.setInsecure();

  HTTPClient http;

  String url = String(firebaseURL) + "test_uid.json";
  http.begin(client, url);

  int httpCode = http.PUT("\"" + uid + "\"");

  Serial.print("Firebase code: ");
  Serial.println(httpCode);
  Serial.println("UID sent to Firebase");

  http.end();
}

bool checkUIDInFirebase(String uid) {

  WiFiClientSecure client;
  client.setInsecure();

  HTTPClient http;

  uid.replace(" ", "");

  String url = String(firebaseURL) + "authorized_uids/" + uid + "/Access.json";

  http.begin(client, url);

  int httpCode = http.GET();

  if (httpCode > 0) {
    String payload = http.getString();

    Serial.print("Firebase response: ");
    Serial.println(payload);

    http.end();

    if (payload == "true") {
      return true;
    }
  }

  http.end();
  return false;
}

void logAccessToFirebase(String uid, String decision) {
  WiFiClientSecure client;
  client.setInsecure();

  HTTPClient http;

  uid.replace(" ", "");

  time_t now = time(nullptr);
  struct tm *timeinfo = localtime(&now);

  char buffer[30];
  strftime(buffer, sizeof(buffer), "%Y-%m-%dT%H:%M:%S", timeinfo);

  String timestamp = String(buffer);

  String url = String(firebaseURL) + "access_logs.json";

  String jsonData = "{";
  jsonData += "\"uid\":\"" + uid + "\",";
  jsonData += "\"decision\":\"" + decision + "\",";
  jsonData += "\"lab_room\":\"lab-101\",";
  jsonData += "\"timestamp\":\"" + timestamp + "\"";
  jsonData += "}";

  http.begin(client, url);
  http.addHeader("Content-Type", "application/json");

  int httpCode = http.POST(jsonData);

  Serial.print("Log code: ");
  Serial.println(httpCode);

  http.end();
}