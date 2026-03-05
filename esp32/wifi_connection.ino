// ESP32 WiFi connection test
// Connects ESP32 to WiFi and prints IP address in Serial Monitor
// Used for NFC Lab Access project communication setup

#include <WiFi.h>
#include <HTTPClient.h>
const char* ssid = "samin";
const char* password = "YOUR_WIFI_PASSWORD";

void setup() {
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
}

void loop() {
}