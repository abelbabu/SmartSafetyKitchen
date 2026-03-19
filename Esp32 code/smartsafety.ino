#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <DHT.h>
#include <time.h>

// ---------------- PIN DEFINITIONS ----------------
#define FLAME_PIN 32
#define GAS_PIN 34
#define DHT_PIN 33
#define RELAY_PIN 23
#define LED_PIN 19
#define RESET_BUTTON 25
#define BUZZER_PIN 18

#define DHTTYPE DHT22
DHT dht(DHT_PIN, DHTTYPE);

// ---------------- WIFI ----------------
#define WIFI_SSID "Abel"
#define WIFI_PASSWORD "00000000"

// ---------------- FIREBASE ----------------
#define API_KEY "AIzaSyBRci3Gafmd9mIPF5Ev4-HY28AckK1B56I"
#define DATABASE_URL "https://smartkitchensafety-1ab6b-default-rtdb.asia-southeast1.firebasedatabase.app/"
#define USER_EMAIL "kitchen@test.com"
#define USER_PASSWORD "12345678"

FirebaseData fbdo;
FirebaseData stream;   // 🔥 NEW STREAM OBJECT
FirebaseAuth auth;
FirebaseConfig config;

// ---------------- TIMERS ----------------
unsigned long cloudTimer = 0;
unsigned long resetCheckTimer = 0;
unsigned long wifiReconnectTimer = 0;

// ---------------- SYSTEM VARIABLES ----------------
bool gasCut = false;
bool flameTimerRunning = false;
bool firebaseStarted = false;

unsigned long flameStartTime = 0;
unsigned long systemStartTime = 0;

String alertType = "none";

// -------- FLAME TIMEOUT --------
unsigned long flameTimeout = 10000; // default

// ---------------- NTP ----------------
const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 19800;
const int daylightOffset_sec = 0;

// ---------------- STREAM CALLBACK ----------------
void streamCallback(FirebaseStream data) {

  if (data.dataType() == "int") {

    int timeoutSec = data.intData();

    if (timeoutSec >= 5 && timeoutSec <= 120) {

      flameTimeout = timeoutSec * 1000;

      Serial.print("🔥 Flame Timeout Updated (Realtime): ");
      Serial.println(timeoutSec);
    }
  }
}

void streamTimeoutCallback(bool timeout) {
  if (timeout) {
    Serial.println("Stream timeout, reconnecting...");
  }
}

// ---------------- TIME ----------------
String getTimeStamp() {
  struct tm timeinfo;
  if(!getLocalTime(&timeinfo)){
    return "00:00:00";
  }
  char buffer[20];
  strftime(buffer,sizeof(buffer),"%H:%M:%S",&timeinfo);
  return String(buffer);
}

// ---------------- LOGGER ----------------
void logEvent(String eventMessage) {
  if (firebaseStarted && Firebase.ready()) {
    String log = getTimeStamp() + " " + eventMessage;
    Firebase.RTDB.pushString(&fbdo, "/Kitchen/logs", log);
  }
}

void setup() {

  Serial.begin(115200);

  pinMode(RELAY_PIN, OUTPUT);
  pinMode(LED_PIN, OUTPUT);
  pinMode(FLAME_PIN, INPUT);
  pinMode(RESET_BUTTON, INPUT_PULLUP);
  pinMode(BUZZER_PIN, OUTPUT);

  digitalWrite(RELAY_PIN, LOW);
  digitalWrite(LED_PIN, LOW);
  digitalWrite(BUZZER_PIN, LOW);

  dht.begin();

  systemStartTime = millis();

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.println("System Booted");

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("WiFi Connected");

  configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
}

void loop() {

  int flameValue = digitalRead(FLAME_PIN);
  int gasValue = analogRead(GAS_PIN);

  // ================= SAFETY SYSTEM =================
  if (millis() - systemStartTime > 10000) {

    if (gasValue > 2000 && !gasCut) {

      gasCut = true;
      alertType = "gas";

      Serial.println("Gas Leak Detected");
      logEvent("Gas Leak Detected");
    }

    if (flameValue == 0) {

      if (!flameTimerRunning) {
        flameTimerRunning = true;
        flameStartTime = millis();
      }

      if (millis() - flameStartTime > flameTimeout && !gasCut) {

        gasCut = true;
        alertType = "flame";

        Serial.println("Flame Timeout");
        logEvent("Flame Timeout");
      }

    } else {
      flameTimerRunning = false;
    }
  }

  // ================= MANUAL RESET =================
  if (digitalRead(RESET_BUTTON) == LOW) {

    if (gasValue < 2000 && flameValue == 1) {

      gasCut = false;
      flameTimerRunning = false;
      alertType = "none";

      Serial.println("Manual Reset");
      logEvent("Manual Reset");

      delay(300);
    }
  }

  // ================= OUTPUT =================
  digitalWrite(RELAY_PIN, gasCut ? HIGH : LOW);
  digitalWrite(LED_PIN, gasCut ? HIGH : LOW);
  digitalWrite(BUZZER_PIN, gasCut ? HIGH : LOW);

  // ================= WIFI RECONNECT =================
  if (WiFi.status() != WL_CONNECTED) {

    if (millis() - wifiReconnectTimer > 10000) {

      wifiReconnectTimer = millis();

      Serial.println("Reconnecting WiFi");

      WiFi.disconnect();
      WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    }
  }

  // ================= FIREBASE INIT =================
  if (WiFi.status() == WL_CONNECTED && !firebaseStarted) {

    config.api_key = API_KEY;
    config.database_url = DATABASE_URL;

    auth.user.email = USER_EMAIL;
    auth.user.password = USER_PASSWORD;

    Firebase.begin(&config, &auth);
    Firebase.reconnectWiFi(true);

    // 🔥 START STREAM HERE
    if (!Firebase.RTDB.beginStream(&stream, "/Kitchen/flame_timeout")) {
      Serial.println("Stream start failed");
    }

    Firebase.RTDB.setStreamCallback(&stream, streamCallback, streamTimeoutCallback);

    firebaseStarted = true;

    Serial.println("Firebase Connected + Streaming Started");
  }

  // ================= REMOTE RESET =================
  if (firebaseStarted && Firebase.ready() && millis() - resetCheckTimer > 3000) {

    resetCheckTimer = millis();

    bool resetCommand;

    if (Firebase.RTDB.getBool(&fbdo, "/Kitchen/reset", &resetCommand)) {

      if (resetCommand && gasValue < 2000 && flameValue == 1) {

        gasCut = false;
        flameTimerRunning = false;
        alertType = "none";

        Serial.println("Remote Reset");
        logEvent("Remote Reset");

        Firebase.RTDB.setBool(&fbdo, "/Kitchen/reset", false);
      }
    }
  }

  // ================= CLOUD UPDATE =================
  if (firebaseStarted && Firebase.ready() && millis() - cloudTimer > 5000) {

    cloudTimer = millis();

    float temperature = dht.readTemperature();
    float humidity = dht.readHumidity();

    Firebase.RTDB.setInt(&fbdo, "/Kitchen/flame", flameValue);
    Firebase.RTDB.setInt(&fbdo, "/Kitchen/gas", gasValue);
    Firebase.RTDB.setFloat(&fbdo, "/Kitchen/temperature", temperature);
    Firebase.RTDB.setFloat(&fbdo, "/Kitchen/humidity", humidity);
    Firebase.RTDB.setBool(&fbdo, "/Kitchen/gas_cut", gasCut);
    Firebase.RTDB.setString(&fbdo, "/Kitchen/alert_type", alertType);
    Firebase.RTDB.setInt(&fbdo, "/Kitchen/last_seen", time(NULL));

    Serial.println("Cloud Updated");
  }}
