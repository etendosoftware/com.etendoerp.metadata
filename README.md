# com.etendoerp.metadata

[![Release](https://img.shields.io/github/v/release/etendosoftware/com.etendoerp.metadata?label=release)](https://github.com/etendosoftware/com.etendoerp.metadata/releases)  
[![Build](https://img.shields.io/github/actions/workflow/status/etendosoftware/com.etendoerp.metadata/gradle.yml?label=build)](https://github.com/etendosoftware/com.etendoerp.metadata/actions)  
[![License](https://img.shields.io/github/license/etendosoftware/com.etendoerp.metadata)](https://github.com/etendosoftware/com.etendoerp.metadata/blob/develop/LICENSE)

---

This module is part of the **EtendoERP** ecosystem and serves as the core metadata module for the system.

It is responsible for exposing key APIs required by the new Etendo UI:  
👉 [com.etendoerp.mainui](https://github.com/etendosoftware/com.etendoerp.mainui)

---

### 📡 Responsibilities

- Provides backend APIs to deliver dynamic metadata to the frontend
- Handles entity definitions, field properties, layout configurations, and more
- Serves as the foundation for rendering dynamic UI components

---

### 📦 Dependencies

This module relies on the following EtendoERP components:

- [`com.etendoerp.openapi`](https://github.com/etendosoftware/com.etendoerp.openapi)
- [`com.etendoerp.etendorx`](https://github.com/etendosoftware/com.etendoerp.etendorx)
- [`com.etendoerp.metadata.template`](https://github.com/etendosoftware/com.etendoerp.metadata.template)

---

### ⚙️ Configuration

To enable authentication for the new UI, you must configure the correct authentication manager in your `Openbravo.properties` file:

```properties
authentication.class=com.etendoerp.etendorx.auth.SWSAuthenticationManager
```

This is required for token-based authentication to work properly with the new frontend.
