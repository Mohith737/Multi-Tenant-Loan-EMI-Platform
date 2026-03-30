# Gmail SMTP App Password Setup Guide

This document explains how to generate and use a **Google App Password** for sending emails using **SMTP (smtp.gmail.com)**.
It is commonly used in backend applications like **Java Spring Boot email services**.

---

# 1. Prerequisites

Before generating an App Password, ensure the following:

* A **Google (Gmail) account**
* **2-Step Verification (2FA)** must be enabled

Without **2FA**, Google will not allow generating an App Password.

---

# 2. Enable 2-Step Verification

1. Open Google Security Settings:

```
https://myaccount.google.com/security
```

2. Navigate to:

```
How you sign in to Google → 2-Step Verification
```

3. Follow the setup process:

* Enter your phone number
* Receive and verify OTP
* Enable 2FA

Once enabled, the **App Passwords** option becomes available.

---

# 3. Generate Gmail App Password

1. Open the App Password page:

```
https://myaccount.google.com/apppasswords
```

2. Login if prompted.

3. Configure the fields:

Select App:

```
Mail
```

Select Device:

```
Other (Custom Name)
```

Enter name:

```
SpringBoot SMTP
```

4. Click **Generate**

Google will generate a **16-character password** like this:

```
abcd efgh ijkl mnop
```

Remove the spaces before using it:

```
REDACTED_SEE_ENV
```

This becomes your **SMTP password**.

---

# 4. Gmail SMTP Configuration

SMTP settings for Gmail:

| Setting        | Value          |
| -------------- | -------------- |
| SMTP Host      | smtp.gmail.com |
| Port           | 587            |
| Encryption     | STARTTLS       |
| Authentication | Required       |

---

# 5. Spring Boot SMTP Configuration

Example **application.yml**

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: your-app-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

---

# 6. Spring Boot Email Service Example

Example email sending service:

```java
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendEmail(String to) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(to);
        message.setSubject("Test Email");
        message.setText("Hello from Spring Boot");

        mailSender.send(message);
    }
}
```

---

# 7. Common Issues

## Authentication Failed

Error example:

```
Username and Password not accepted
```

Cause:

Using Gmail password instead of **App Password**

Fix:

Use the **16-character App Password**

---

## App Password Option Not Visible

Possible reasons:

* 2FA not enabled
* Google Workspace admin disabled it
* Account uses Advanced Protection

---

# 8. Security Best Practices

Never store passwords directly in code.

Use:

* Environment variables
* Secret manager
* Vault

Example:

```yaml
spring:
  mail:
    password: ${SMTP_PASSWORD}
```

Set environment variable:

```
export SMTP_PASSWORD=REDACTED_SEE_ENV
```

---

# 9. Recommended Production Alternative

For production systems, Google recommends using:

**Gmail API with OAuth2 authentication**

Advantages:

* No password storage
* Token-based authentication
* Higher security
* Better integration

---

# 10. Summary

Steps to use Gmail SMTP in applications:

1. Enable **2-Step Verification**
2. Generate **Google App Password**
3. Configure **SMTP settings**
4. Use in **Spring Boot Mail configuration**
5. Store credentials securely using environment variables

---

# Quick SMTP Reference

SMTP Host:

```
smtp.gmail.com
```

Port:

```
587
```

Security:

```
STARTTLS
```

Authentication:

```
App Password
```


