# Beauty & Barbershop Booking Platform - Backend

A production-ready Java Spring Boot backend for a SaaS beauty and barbershop appointment booking platform.

# The docs can be seen [here](http://localhost:8080/swagger-ui/index.html)

## Features

- **User Management**: Platform admin and business admin roles
- **Business Management**: Create and manage multiple businesses with slug-based routing
- **Employee Management**: Manage staff members for each business
- **Service Management**: Define and manage services with pricing and duration
- **Booking System**: Guest-friendly booking without authentication required
- **Review System**: Collect customer feedback with comprehensive ratings
- **Analytics Dashboard**: Track bookings, ratings, and performance metrics
- **JWT Authentication**: Secure role-based access control
- **CORS Support**: Ready for React/Vite frontend integration
- **Swagger/OpenAPI Documentation**: Interactive API documentation
- **Time Slot Generation**: Automatic availability slot generation
- **Slug Generation**: SEO-friendly business URLs

## Tech Stack

- **Java 17+**
- **Spring Boot 3.2.0**
- **Spring Security + JWT**
- **Spring Data JPA with Hibernate**
- **PostgreSQL**
- **Maven**
- **Lombok**
- **Swagger/OpenAPI**
- **MapStruct** (DTO mapping)

## Project Structure

```
src/main/java/com/platform/
├── config/              # Security, CORS, OpenAPI configurations
├── controller/          # REST API endpoints
├── dto/                 # Data Transfer Objects
├── entity/              # JPA entities
├── exception/           # Exception handling
├── repository/          # Spring Data JPA repositories
├── security/            # JWT authentication filter
├── providedService/             # Business logic layer
└── utils/               # Utility classes
```

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- PostgreSQL 12+
- Git

## Setup & Installation

### 1. Clone the Repository
```bash
git clone <repository-url>
cd beauty-booking-platform
```

### 2. Configure Environment Variables

Copy `.env.example` to `.env` and configure your database:

```bash
cp secrets.properties.dev secrets.properties
```

Update `.env` with your PostgreSQL credentials:

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=beauty_booking
DB_USER=postgres
DB_PASSWORD=your_password

JWT_SECRET=your_secure_jwt_secret_32_chars_minimum
JWT_EXPIRATION=86400000

SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev
```

### 3. Create Database

```bash
# Using PostgreSQL CLI
createdb beauty_booking
```

### 4. Build the Project

```bash
mvn clean build
```

### 5. Run the Application

```bash
mvn spring-boot:run
```

The application will start at `http://localhost:8080`

## API Endpoints

### Authentication
- `POST /api/auth/register` - Register new business admin
- `POST /api/auth/login` - Login and get JWT token

### Business
- `GET /api/business` - List all businesses (with filters)
- `GET /api/business/:id` - Get business by ID
- `GET /api/business/slug/:slug` - Get business by slug
- `POST /api/business` - Create business (requires auth)
- `PUT /api/business/:id` - Update business (requires auth)
- `DELETE /api/business/:id` - Delete business (requires auth)
- `GET /api/business/user/my-businesses` - Get user's businesses

### Employees
- `GET /api/business/:businessId/employee` - List employees
- `GET /api/business/:businessId/employee/active` - List active employees
- `POST /api/business/:businessId/employee` - Create employee
- `PUT /api/business/:businessId/employee/:employeeId` - Update employee
- `DELETE /api/business/:businessId/employee/:employeeId` - Delete employee

### Services
- `GET /api/business/:businessId/providedService` - List services
- `GET /api/business/:businessId/providedService/active` - List active services
- `POST /api/business/:businessId/providedService` - Create providedService
- `PUT /api/business/:businessId/providedService/:serviceId` - Update providedService
- `DELETE /api/business/:businessId/providedService/:serviceId` - Delete providedService

### Bookings
- `POST /api/booking` - Create booking (no auth required)
- `GET /api/booking/:id` - Get booking details
- `GET /api/booking` - List bookings (with filters)
- `GET /api/booking/employee/:employeeId/range` - Get bookings by date range
- `PATCH /api/booking/:id/status` - Update booking status
- `DELETE /api/booking/:id` - Cancel booking
- `GET /api/booking/business/:businessId` - Get business bookings

### Reviews
- `POST /api/review/booking/:bookingId` - Add review
- `GET /api/review/:id` - Get review
- `GET /api/review/business/:businessId` - List business reviews
- `PATCH /api/review/:id/reply` - Add business reply
- `GET /api/review/business/:businessId/average` - Get average rating

### Analytics
- `GET /api/analytics/business/:businessId/dashboard` - Business dashboard

## API Documentation

Interactive Swagger documentation is available at:
```
http://localhost:8080/swagger-ui.html
```

JSON API docs:
```
http://localhost:8080/v3/api-docs
```

## Authentication

All protected endpoints require JWT token in the Authorization header:

```
Authorization: Bearer <your_jwt_token>
```

### User Roles

- **PLATFORM_ADMIN**: Full platform access
- **BUSINESS_ADMIN**: Business-specific management

## Database Schema

### Tables
- `users` - User accounts
- `businesses` - Business profiles
- `employees` - Business staff members
- `services` - Service offerings
- `bookings` - Customer bookings
- `reviews` - Customer reviews

## Key Features Explained

### Time Slot Generation
Generate available time slots based on providedService duration:
```java
List<LocalDateTime> slots = TimeSlotGenerator.generateAvailableSlots(
    LocalDateTime.now(),
    60 // providedService duration in minutes
);
```

### Slug Generation
Generate SEO-friendly URLs:
```java
String slug = SlugGenerator.generate("The Beauty Salon");
// Output: the-beauty-salon
```

### JWT Token
Tokens include user ID, email, and role:
```
Header: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## Error Handling

All errors return structured responses:

```json
{
  "timestamp": "2024-02-08T10:30:00",
  "status": 400,
  "error": "Validation Error",
  "message": "Invalid request parameters",
  "path": "/api/business",
  "validationErrors": {
    "name": "Business name is required"
  }
}
```

## Development

### Running with Dev Profile
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

### Creating Migrations
Database migrations use Hibernate's `ddl-auto` in dev mode. For production, use proper migration scripts.

## Security Considerations

- Passwords are encrypted with BCrypt
- JWT tokens expire after 24 hours (configurable)
- CORS is configured for frontend domains
- Role-based access control on all protected endpoints
- Input validation on all request DTOs
- SQL injection prevention through JPA parameterized queries

## Performance Optimizations

- Database connection pooling with HikariCP
- Lazy loading for JPA relationships
- Query optimization with proper indexes
- Pagination support for list endpoints

## Deployment

### Building for Production
```bash
mvn clean package -DskipTests
```
Produces `target/app.jar` — the name is fixed by `<finalName>app</finalName>` in the pom, so it does not move when the project version changes.

### Docker Deployment
The repository root holds a multi-stage `Dockerfile` (Maven build → `eclipse-temurin:17-jre-jammy`, non-root `app` user). It is self-contained, so a clean checkout needs no prior Maven run:
```bash
docker build -t backend-platform .
```
If you have already run `mvn package`, pass the build arg CI uses and the image will copy that jar instead of compiling a second time:
```bash
mvn -B package
docker build --build-arg JAR_SOURCE=prebuilt -t backend-platform .
```
Requires BuildKit, which is the default in Docker 23+.

### Environment Variables for Production
Set environment variables for your production database:
```bash
export DB_HOST=your-prod-host
export DB_PORT=5432
export DB_NAME=beauty_booking_prod
export DB_USER=prod_user
export DB_PASSWORD=secure_password
export JWT_SECRET=your_long_secure_secret
export JWT_EXPIRATION=86400000
```

## Troubleshooting

### Database Connection Issues
- Verify PostgreSQL is running
- Check `.env` credentials
- Ensure database exists: `createdb beauty_booking`

### JWT Token Errors
- Ensure `JWT_SECRET` is at least 32 characters
- Check token expiration time
- Verify Bearer token format in Authorization header

### CORS Issues
- Check frontend origin is allowed in `CorsConfig`
- Verify `*` wildcard or specific domains are configured

## Contributing

1. Follow Java naming conventions
2. Add tests for new features
3. Update API documentation
4. Keep commit messages clear

## License

Proprietary - All rights reserved

## Support

For issues and questions, contact the development team.
