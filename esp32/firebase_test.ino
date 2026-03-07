// ESP32 WiFi connection test
// Connects ESP32 to WiFi and prints IP address in Serial Monitor
// Used for NFC Lab Access project communication setup

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <time.h>
const char* firebaseURL = "https://nfc-lab-access-app-default-rtdb.firebaseio.com/";
const char* ssid = "samin";
const char* password = "password123";
int peopleCount = 5;
#define IR_PINA 32
#define IR_PINB 33

#define LED_RED 14
#define LED_GREEN 26

char firstBeam = 0;
unsigned long beamTimer = 0;
const unsigned long beamTimeout = 400;

void setup() {
  delay(1000);
  Serial.begin(115200);
  pinMode(IR_PINA, INPUT_PULLUP);
  pinMode(IR_PINB, INPUT_PULLUP);

  pinMode(LED_RED, OUTPUT);
  pinMode(LED_GREEN, OUTPUT);
  delay(500);

  Serial.println();
  Serial.print("Connecting to WiFi: ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\n✅ WiFi connected!");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());

  configTime(0, 0, "time.google.com", "pool.ntp.org", "time.cloudflare.com");
  Serial.print("Syncing time");
  time_t now = time(nullptr);
  int retries = 20;
  while (now < 100000 && retries > 0) {
   delay(500);
   Serial.print(".");
   now = time(nullptr);
   retries--;
  }
  Serial.println("\nTime synced!"); 
}

void loop() {
  
  bool a = digitalRead(IR_PINA);
  bool b = digitalRead(IR_PINB);

  digitalWrite(LED_GREEN, a == LOW ? LOW : HIGH);
  digitalWrite(LED_RED,   b == LOW ? LOW : HIGH);

  if (firstBeam == 0) {
  if (a == LOW) {
    firstBeam = 'A';
    beamTimer = millis();
  }
  else if (b == LOW) {
    firstBeam = 'B';
    beamTimer = millis();
  }
}
  if (firstBeam == 'A' && b == LOW) {
  peopleCount++;
  Serial.print("ENTERED | Count: ");
  Serial.println(peopleCount);
  waitForClear();
  firstBeam = 0;
}

if (firstBeam == 'B' && a == LOW) {
  peopleCount--;
  if (peopleCount < 0) peopleCount = 0;

  Serial.print("EXIT | Count: ");
  Serial.println(peopleCount);
  waitForClear();
  firstBeam = 0;
}
if (firstBeam != 0 && (millis() - beamTimer > beamTimeout)) {
  firstBeam = 0;
}

  WiFiClientSecure client;
  client.setInsecure();

  HTTPClient http;

  http.begin(client, "https://nfc-lab-access-app-default-rtdb.firebaseio.com/occupancy/lab-101/current_count.json");

  int code = http.PUT(String(peopleCount));

  Serial.print("Firebase test code: ");
  Serial.println(code);

  String resp = http.getString();
  Serial.print("Firebase response length: ");
  Serial.println(resp.length());

  http.end();
  delay(10);  
}

void waitForClear() {
  while (digitalRead(IR_PINA) == LOW || digitalRead(IR_PINB) == LOW) {
    delay(5);
  }
}