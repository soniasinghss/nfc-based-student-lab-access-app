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

void setup() {
  delay(1000);
  Serial.begin(115200);
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
  WiFiClientSecure client;
client.setInsecure();

HTTPClient http;

http.begin(client, "https://nfc-lab-access-app-default-rtdb.firebaseio.com/occupancy/lab-101/current_count.json");

int peopleCount = 5;
int code = http.PUT(String(peopleCount));

Serial.print("Firebase test code: ");
Serial.println(code);

String resp = http.getString();
Serial.print("Firebase response length: ");
Serial.println(resp.length());

http.end();
  

  
}

void loop() {
}