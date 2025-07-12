# Simple Attendance iOS App Implementation Guide

## Overview
This document outlines the implementation approach for creating the Simple Attendance app for iOS, based on the existing Android implementation and PRD requirements.

## Project Setup

### 1. Development Environment
- **Xcode**: Latest version (15.0+)
- **iOS Target**: iOS 13.0+ (for wider device compatibility)
- **Language**: Swift 5.0+
- **Architecture**: UIKit with MVC pattern (lightweight approach)

### 2. Project Structure
```
SimpleAttendance/
├── SimpleAttendance/
│   ├── Models/
│   │   ├── Attendance.swift
│   │   ├── Profile.swift
│   │   ├── WifiAllowed.swift
│   │   └── AuthResponse.swift
│   ├── Views/
│   │   ├── Storyboards/
│   │   │   ├── Main.storyboard
│   │   │   └── Login.storyboard
│   │   └── Custom/
│   │       └── AttendanceDetailView.swift
│   ├── Controllers/
│   │   ├── LoginViewController.swift
│   │   ├── MainViewController.swift
│   │   └── CalendarViewController.swift
│   ├── Services/
│   │   ├── APIClient.swift
│   │   ├── SessionManager.swift
│   │   └── NetworkManager.swift
│   ├── Utils/
│   │   ├── Constants.swift
│   │   ├── Extensions.swift
│   │   └── DateHelper.swift
│   └── Resources/
│       ├── Assets.xcassets
│       └── Info.plist
├── Podfile (if using CocoaPods)
└── README.md
```

## Dependencies

### Minimal Dependencies Approach
**No external dependencies required** - Use native iOS frameworks:
- `Foundation` - Core functionality
- `UIKit` - User interface
- `NetworkExtension` - WiFi SSID detection
- `SystemConfiguration` - Network reachability

### Alternative (If preferred):
```ruby
# Podfile
platform :ios, '13.0'
use_frameworks!

target 'SimpleAttendance' do
  pod 'Alamofire', '~> 5.0'  # HTTP networking (optional)
end
```

## Core Implementation

### 1. Models (Swift Structs)

```swift
// Models/Attendance.swift
struct Attendance: Codable {
    let id: String?
    let employeeId: String
    let attendanceDate: String
    let inTime: String?
    let outTime: String?
    let totalMinutes: Int?
    let status: String?
    
    enum CodingKeys: String, CodingKey {
        case id
        case employeeId = "employee_id"
        case attendanceDate = "attendance_date"
        case inTime = "in_time"
        case outTime = "out_time"
        case totalMinutes = "total_minutes"
        case status
    }
}

// Models/Profile.swift
struct Profile: Codable {
    let id: String
    let email: String?
    let firstName: String?
    let lastName: String?
    let empCode: String
    let isActive: Bool
    let androidLogin: Bool
    let deviceId: String?
    
    enum CodingKeys: String, CodingKey {
        case id, email
        case firstName = "first_name"
        case lastName = "last_name"
        case empCode = "emp_code"
        case isActive = "is_active"
        case androidLogin = "android_login"
        case deviceId = "device_id"
    }
}

// Models/WifiAllowed.swift
struct WifiAllowed: Codable {
    let id: String
    let ssid: String
    let bssid: String
    let location: String
    let isActive: Bool
    
    enum CodingKeys: String, CodingKey {
        case id, ssid, bssid, location
        case isActive = "is_active"
    }
}
```

### 2. API Client

```swift
// Services/APIClient.swift
import Foundation

class APIClient {
    static let shared = APIClient()
    private let baseURL = "YOUR_SUPABASE_URL"
    private let apiKey = "YOUR_SUPABASE_ANON_KEY"
    
    private init() {}
    
    func makeRequest(
        endpoint: String,
        method: HTTPMethod = .GET,
        body: Data? = nil,
        token: String? = nil,
        completion: @escaping (Result<Data, Error>) -> Void
    ) {
        guard let url = URL(string: baseURL + endpoint) else {
            completion(.failure(APIError.invalidURL))
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "apikey")
        
        if let token = token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        if let body = body {
            request.httpBody = body
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let data = data else {
                completion(.failure(APIError.noData))
                return
            }
            
            completion(.success(data))
        }.resume()
    }
}

enum HTTPMethod: String {
    case GET = "GET"
    case POST = "POST"
    case PATCH = "PATCH"
    case DELETE = "DELETE"
}

enum APIError: Error {
    case invalidURL
    case noData
    case decodingError
}
```

### 3. Session Manager

```swift
// Services/SessionManager.swift
import Foundation

class SessionManager {
    static let shared = SessionManager()
    private let userDefaults = UserDefaults.standard
    
    private enum Keys {
        static let accessToken = "access_token"
        static let userId = "user_id"
        static let email = "email"
        static let firstName = "first_name"
        static let empCode = "emp_code"
        static let isLoggedIn = "is_logged_in"
    }
    
    private init() {}
    
    func saveSession(token: String, userId: String, email: String) {
        userDefaults.set(token, forKey: Keys.accessToken)
        userDefaults.set(userId, forKey: Keys.userId)
        userDefaults.set(email, forKey: Keys.email)
        userDefaults.set(true, forKey: Keys.isLoggedIn)
    }
    
    func saveUserProfile(firstName: String?, empCode: String?) {
        userDefaults.set(firstName, forKey: Keys.firstName)
        userDefaults.set(empCode, forKey: Keys.empCode)
    }
    
    var accessToken: String? {
        return userDefaults.string(forKey: Keys.accessToken)
    }
    
    var userId: String? {
        return userDefaults.string(forKey: Keys.userId)
    }
    
    var email: String? {
        return userDefaults.string(forKey: Keys.email)
    }
    
    var firstName: String? {
        return userDefaults.string(forKey: Keys.firstName)
    }
    
    var empCode: String? {
        return userDefaults.string(forKey: Keys.empCode)
    }
    
    var isLoggedIn: Bool {
        return userDefaults.bool(forKey: Keys.isLoggedIn)
    }
    
    func clearSession() {
        userDefaults.removeObject(forKey: Keys.accessToken)
        userDefaults.removeObject(forKey: Keys.userId)
        userDefaults.removeObject(forKey: Keys.email)
        userDefaults.removeObject(forKey: Keys.firstName)
        userDefaults.removeObject(forKey: Keys.empCode)
        userDefaults.removeObject(forKey: Keys.isLoggedIn)
    }
    
    var deviceId: String {
        return UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
    }
}
```

### 4. WiFi Detection

```swift
// Services/WiFiManager.swift
import Foundation
import SystemConfiguration.CaptiveNetwork
import NetworkExtension

class WiFiManager {
    static let shared = WiFiManager()
    
    private init() {}
    
    func getCurrentWiFiSSID() -> String? {
        var ssid: String?
        
        if let interfaces = CNCopySupportedInterfaces() as NSArray? {
            for interface in interfaces {
                if let interfaceInfo = CNCopyCurrentNetworkInfo(interface as! CFString) as NSDictionary? {
                    ssid = interfaceInfo[kCNNetworkInfoKeySSID as String] as? String
                    break
                }
            }
        }
        
        return ssid
    }
    
    func getCurrentWiFiBSSID() -> String? {
        var bssid: String?
        
        if let interfaces = CNCopySupportedInterfaces() as NSArray? {
            for interface in interfaces {
                if let interfaceInfo = CNCopyCurrentNetworkInfo(interface as! CFString) as NSDictionary? {
                    bssid = interfaceInfo[kCNNetworkInfoKeyBSSID as String] as? String
                    break
                }
            }
        }
        
        return bssid?.lowercased()
    }
}
```

### 5. Main View Controller

```swift
// Controllers/MainViewController.swift
import UIKit

class MainViewController: UIViewController {
    @IBOutlet weak var greetingLabel: UILabel!
    @IBOutlet weak var loginIdLabel: UILabel!
    @IBOutlet weak var dateLabel: UILabel!
    @IBOutlet weak var markInButton: UIButton!
    @IBOutlet weak var markOutButton: UIButton!
    @IBOutlet weak var viewAttendanceButton: UIButton!
    @IBOutlet weak var statusLabel: UILabel!
    @IBOutlet weak var activityIndicator: UIActivityIndicatorView!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        guard SessionManager.shared.isLoggedIn else {
            navigateToLogin()
            return
        }
        
        setupUI()
        loadUserProfile()
        loadTodayAttendance()
        updateDateDisplay()
    }
    
    private func setupUI() {
        markInButton.layer.cornerRadius = 8
        markOutButton.layer.cornerRadius = 8
        viewAttendanceButton.layer.cornerRadius = 8
        
        markInButton.backgroundColor = .systemGreen
        markOutButton.backgroundColor = .systemOrange
        viewAttendanceButton.backgroundColor = .systemBlue
    }
    
    private func loadUserProfile() {
        // Check cached profile data
        if let firstName = SessionManager.shared.firstName,
           let empCode = SessionManager.shared.empCode {
            greetingLabel.text = "Hi \(firstName)"
            loginIdLabel.text = "Login ID: \(empCode)"
            return
        }
        
        // Fetch from API if not cached
        guard let userId = SessionManager.shared.userId,
              let token = SessionManager.shared.accessToken else { return }
        
        let endpoint = "/profile?id=eq.\(userId)&select=first_name,emp_code"
        
        APIClient.shared.makeRequest(endpoint: endpoint, token: token) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let data):
                    if let profiles = try? JSONDecoder().decode([Profile].self, from: data),
                       let profile = profiles.first {
                        let firstName = profile.firstName ?? "User"
                        let empCode = profile.empCode
                        
                        SessionManager.shared.saveUserProfile(firstName: firstName, empCode: empCode)
                        
                        self?.greetingLabel.text = "Hi \(firstName)"
                        self?.loginIdLabel.text = "Login ID: \(empCode)"
                    }
                case .failure(_):
                    self?.greetingLabel.text = "Hi User"
                    self?.loginIdLabel.text = "Login ID: N/A"
                }
            }
        }
    }
    
    private func updateDateDisplay() {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE, MMMM dd, yyyy"
        dateLabel.text = formatter.string(from: Date())
    }
    
    @IBAction func markInTapped(_ sender: UIButton) {
        markAttendance(isMarkIn: true)
    }
    
    @IBAction func markOutTapped(_ sender: UIButton) {
        markAttendance(isMarkIn: false)
    }
    
    @IBAction func viewAttendanceTapped(_ sender: UIButton) {
        performSegue(withIdentifier: "showCalendar", sender: nil)
    }
    
    private func markAttendance(isMarkIn: Bool) {
        validateWiFiAndMark(isMarkIn: isMarkIn)
    }
    
    private func validateWiFiAndMark(isMarkIn: Bool) {
        guard let bssid = WiFiManager.shared.getCurrentWiFiBSSID() else {
            showError("Unable to get WiFi information")
            return
        }
        
        setLoading(true)
        
        let endpoint = "/wifi_allowed?bssid=eq.\(bssid)"
        
        APIClient.shared.makeRequest(endpoint: endpoint, token: SessionManager.shared.accessToken) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let data):
                    if let wifiList = try? JSONDecoder().decode([WifiAllowed].self, from: data),
                       !wifiList.isEmpty {
                        self?.performAttendanceMark(isMarkIn: isMarkIn)
                    } else {
                        self?.setLoading(false)
                        self?.showError("WiFi not approved. BSSID: \(bssid)")
                    }
                case .failure(_):
                    self?.setLoading(false)
                    self?.showError("Network error")
                }
            }
        }
    }
    
    private func setLoading(_ loading: Bool) {
        if loading {
            activityIndicator.startAnimating()
        } else {
            activityIndicator.stopAnimating()
        }
        
        markInButton.isEnabled = !loading
        markOutButton.isEnabled = !loading
        viewAttendanceButton.isEnabled = !loading
    }
    
    private func showError(_ message: String) {
        statusLabel.text = message
        statusLabel.textColor = .systemRed
        statusLabel.isHidden = false
        
        let alert = UIAlertController(title: "Error", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
    
    private func navigateToLogin() {
        performSegue(withIdentifier: "showLogin", sender: nil)
    }
}
```

## iOS-Specific Considerations

### 1. WiFi Access Permissions
- **Info.plist**: Add usage description for location access
```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>This app needs location access to verify WiFi for attendance marking.</string>
```

### 2. App Transport Security
```xml
<!-- Info.plist -->
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <false/>
    <key>NSExceptionDomains</key>
    <dict>
        <key>your-supabase-domain.supabase.co</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <false/>
            <key>NSExceptionMinimumTLSVersion</key>
            <string>TLSv1.2</string>
        </dict>
    </dict>
</dict>
```

### 3. Background App Refresh
```xml
<key>UIBackgroundModes</key>
<array>
    <string>background-processing</string>
</array>
```

## UI Implementation

### 1. Storyboard Layout
- Use Auto Layout constraints for different screen sizes
- Support both portrait and landscape orientations
- Implement accessibility features (VoiceOver support)

### 2. Color Scheme
```swift
// Utils/Colors.swift
extension UIColor {
    static let primaryColor = UIColor.systemBlue
    static let successColor = UIColor.systemGreen
    static let warningColor = UIColor.systemOrange
    static let errorColor = UIColor.systemRed
    static let presentColor = UIColor.systemGreen
    static let lateColor = UIColor.systemYellow
    static let absentColor = UIColor.systemRed
    static let holidayColor = UIColor.systemGray
}
```

### 3. Calendar Implementation
```swift
// Controllers/CalendarViewController.swift
import UIKit

class CalendarViewController: UIViewController {
    @IBOutlet weak var collectionView: UICollectionView!
    @IBOutlet weak var monthYearLabel: UILabel!
    
    private var calendar = Calendar.current
    private var currentDate = Date()
    private var attendanceData: [String: Attendance] = [:]
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupCollectionView()
        loadAttendanceData()
        updateMonthDisplay()
    }
    
    private func setupCollectionView() {
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 1
        layout.minimumLineSpacing = 1
        
        let width = (view.frame.width - 20) / 7
        layout.itemSize = CGSize(width: width, height: width)
        
        collectionView.collectionViewLayout = layout
    }
}

// Calendar Cell
class CalendarCell: UICollectionViewCell {
    @IBOutlet weak var dayLabel: UILabel!
    
    func configure(day: Int, status: String?) {
        dayLabel.text = "\(day)"
        
        switch status {
        case "Present":
            backgroundColor = .presentColor
            dayLabel.textColor = .white
        case "Late":
            backgroundColor = .lateColor
            dayLabel.textColor = .black
        case "Absent":
            backgroundColor = .absentColor
            dayLabel.textColor = .white
        case "Holiday":
            backgroundColor = .holidayColor
            dayLabel.textColor = .white
        default:
            backgroundColor = .systemBackground
            dayLabel.textColor = .label
        }
        
        layer.cornerRadius = 4
    }
}
```

## Build Configuration

### 1. App Icon and Launch Screen
- Create app icons for all required sizes (20x20 to 1024x1024)
- Design launch screen matching app theme
- Ensure proper asset catalog setup

### 2. Build Settings
```swift
// Deployment Target: iOS 13.0
// Swift Language Version: Swift 5
// Code Signing: Development/Distribution profiles
// Bundle Identifier: com.company.simpleattendance
```

### 3. Optimization
- Enable bitcode for App Store distribution
- Configure release build optimizations
- Minimize app size using asset optimization

## Testing Strategy

### 1. Unit Tests
```swift
// SimpleAttendanceTests/SessionManagerTests.swift
import XCTest
@testable import SimpleAttendance

class SessionManagerTests: XCTestCase {
    func testSessionSaving() {
        let sessionManager = SessionManager.shared
        sessionManager.saveSession(token: "test_token", userId: "test_id", email: "test@example.com")
        
        XCTAssertEqual(sessionManager.accessToken, "test_token")
        XCTAssertEqual(sessionManager.userId, "test_id")
        XCTAssertTrue(sessionManager.isLoggedIn)
    }
}
```

### 2. UI Tests
```swift
// SimpleAttendanceUITests/LoginUITests.swift
import XCTest

class LoginUITests: XCTestCase {
    func testLoginFlow() {
        let app = XCUIApplication()
        app.launch()
        
        let emailField = app.textFields["emailTextField"]
        let passwordField = app.secureTextFields["passwordTextField"]
        let loginButton = app.buttons["loginButton"]
        
        emailField.tap()
        emailField.typeText("test@example.com")
        
        passwordField.tap()
        passwordField.typeText("password")
        
        loginButton.tap()
        
        // Assert navigation to main screen
        XCTAssertTrue(app.buttons["markInButton"].exists)
    }
}
```

## Deployment

### 1. App Store Requirements
- App Store Connect account setup
- App metadata and screenshots
- App Store Review Guidelines compliance
- Privacy policy and terms of service

### 2. Distribution Methods
- **TestFlight**: For beta testing
- **App Store**: For public release
- **Enterprise**: For internal distribution (if applicable)

### 3. Release Checklist
- [ ] All features implemented and tested
- [ ] UI/UX reviewed and approved
- [ ] Performance testing completed
- [ ] Security review conducted
- [ ] App Store metadata prepared
- [ ] Screenshots and videos created
- [ ] Privacy policy updated
- [ ] Release notes prepared

## Migration from Android

### Key Differences to Consider

1. **Navigation Patterns**
   - iOS: Navigation Controller stack
   - Android: Activity-based navigation

2. **UI Guidelines**
   - iOS: Human Interface Guidelines
   - Android: Material Design

3. **Permissions**
   - iOS: Runtime permissions with usage descriptions
   - Android: Manifest and runtime permissions

4. **Data Storage**
   - iOS: UserDefaults, Keychain, Core Data
   - Android: SharedPreferences, Room, SQLite

5. **Background Processing**
   - iOS: More restrictive background execution
   - Android: More flexible background services

### Code Reuse Opportunities
- API endpoints and data models structure
- Business logic and validation rules
- Database schema and Supabase configuration
- Time zone handling and date formatting logic

## Timeline Estimate

### Development Phases
1. **Setup & Configuration**: 2-3 days
2. **Core Features Implementation**: 5-7 days
3. **UI/UX Polish**: 2-3 days
4. **Testing & Bug Fixes**: 3-4 days
5. **App Store Preparation**: 1-2 days

**Total Estimated Time**: 13-19 days

### Team Requirements
- **iOS Developer**: 1 senior developer
- **UI/UX Designer**: 0.5 FTE for design review
- **QA Tester**: 0.5 FTE for testing
- **Project Manager**: 0.2 FTE for coordination

## Conclusion

This iOS implementation will provide feature parity with the Android version while following iOS-specific design patterns and best practices. The lightweight approach using native frameworks ensures optimal performance and minimal app size, meeting the same efficiency requirements as the Android version.

The modular architecture allows for easy maintenance and future enhancements, while the comprehensive testing strategy ensures reliability and quality for production deployment.