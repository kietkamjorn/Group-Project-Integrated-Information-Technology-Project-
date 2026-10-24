# ITB MShop — Full-Stack E-Commerce Platform

ITB MShop is a full-stack e-commerce web application built with Vue.js, Spring Boot, and MySQL. It allows buyers to browse products, manage their shopping carts, and place orders, while sellers can manage product listings and track sales.

The application is deployed on Oracle Cloud Infrastructure (OCI) using Docker Compose.

---

## Live Demo & Test Accounts

- **Live Demo:** [http://168.110.9.184](http://168.110.9.184)

### Demo Accounts

Use the following accounts to explore the application's buyer and seller features.

| Role | Email | Password |
|---|---|---|
| Seller | `seller5@mail.com` | `Seller@itbm1` |
| Buyer | `Buyer@mail.com` | `Buyer@itbm1` |

**Seller:** Manage product listings, perform CRUD operations, and access sales management features.

**Buyer:** Browse products, add items to the shopping cart, and place orders.

---

## Key Features

- **Authentication & Authorization:** JWT-based authentication and role-based access control (RBAC) for buyers and sellers.
- **Product Management:** Create, view, update, and delete product listings.
- **Email Verification:** Support account activation through email verification using Jakarta Mail and SMTP.
- **Shopping Cart & Orders:** Add products to the cart, place orders, and track order status.
- **Cloud Deployment:** Run the application in Docker containers on an Oracle Cloud VM, with Nginx configured as a reverse proxy.

---

## Tech Stack

- **Frontend:** Vue.js 3, Vue Router, Tailwind CSS, Vite
- **Backend:** Java 17, Spring Boot 3, Spring Security, JWT, Spring Data JPA, Hibernate, Jakarta Mail
- **Database:** MySQL 8.0
- **Testing:** Cypress (End-to-End Testing)
- **Deployment & Infrastructure:** Oracle Cloud Infrastructure (OCI), Linux, Docker, Docker Compose, Nginx
- **Development Tools:** VS Code, DBeaver, Postman, Git

---

## Automated Testing

Cypress is used to automate end-to-end tests for important user workflows and help identify issues before they affect the user experience.

### Test Scenarios

- **Authentication:** Test sign-in and registration workflows.
- **Role-Based Access Control:** Verify that buyers and sellers can access the appropriate features and pages, including the seller's product management page (`/list`).
- **Product & Shopping Cart:** Test product browsing and adding items to the shopping cart.
- **Order Workflow:** Test the order placement process.

