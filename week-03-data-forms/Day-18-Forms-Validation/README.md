# Day 18 - Forms & Validation

## Mục tiêu
- Dùng Play Form API để validate input
- Bean Validation annotations
- Trả error messages chuẩn cho REST API

---

## 1. Form DTO Class

```java
// app/forms/CreateTodoForm.java
package forms;

import play.data.validation.Constraints;

public class CreateTodoForm {

    @Constraints.Required(message = "Title is required")
    @Constraints.MaxLength(value = 255, message = "Title must not exceed 255 characters")
    @Constraints.MinLength(value = 1, message = "Title cannot be empty")
    public String title;

    public boolean done = false;

    // Custom validation method - phải trả String (error message) hoặc null (valid)
    public String validate() {
        if (title != null && title.contains("<script>")) {
            return "Title contains invalid characters";
        }
        return null;
    }
}
```

---

## 2. Bean Validation Annotations

```java
// Các annotation thông dụng từ javax.validation
import javax.validation.constraints.*;

public class UserForm {
    @NotNull
    @NotBlank
    @Size(min = 2, max = 100)
    public String name;

    @Email(message = "Invalid email format")
    @NotBlank
    public String email;

    @Min(value = 0, message = "Age must be non-negative")
    @Max(value = 150, message = "Age must be realistic")
    public Integer age;

    @Pattern(regexp = "^[0-9]{10,11}$", message = "Phone must be 10-11 digits")
    public String phone;

    @NotNull
    public String role;

    @AssertTrue(message = "Must accept terms")
    public boolean acceptedTerms;
}
```

---

## 3. Validate Trong Controller

```java
import play.data.Form;
import play.data.FormFactory;
import play.mvc.*;
import forms.CreateTodoForm;

public class TodoController extends Controller {

    private final FormFactory formFactory;

    @Inject
    public TodoController(FormFactory formFactory) {
        this.formFactory = formFactory;
    }

    public Result create(Http.Request request) {
        // 1. Bind form từ request body (JSON hoặc form data)
        Form<CreateTodoForm> form = formFactory
            .form(CreateTodoForm.class)
            .bindFromRequest(request);

        // 2. Kiểm tra lỗi
        if (form.hasErrors()) {
            // Trả lỗi dạng JSON cho REST API
            return badRequest(form.errorsAsJson());
        }

        // 3. Lấy dữ liệu validated
        CreateTodoForm data = form.get();

        // 4. Xử lý
        Todo todo = todoRepository.save(data.title);
        return created(Json.toJson(todo));
    }
}
```

---

## 4. Error Response Format

Khi có validation error, `form.errorsAsJson()` trả về:

```json
{
    "title": [
        {"message": "error.required", "args": []}
    ],
    "email": [
        {"message": "error.email", "args": []}
    ]
}
```

**Custom format cho REST API:**

```java
private ObjectNode buildErrorResponse(Form<?> form) {
    ObjectNode errors = Json.newObject();
    form.errors().forEach((field, fieldErrors) -> {
        com.fasterxml.jackson.databind.node.ArrayNode errorArray = errors.putArray(field);
        fieldErrors.forEach(error ->
            errorArray.add(error.message())
        );
    });

    ObjectNode response = Json.newObject();
    response.put("status", "error");
    response.set("errors", errors);
    return response;
}

// Response:
// {
//   "status": "error",
//   "errors": {
//     "title": ["Title is required"],
//     "email": ["Invalid email format"]
//   }
// }
```

---

## 5. Validation Với JSON Input Trực Tiếp (Không Dùng Form API)

Với REST API thuần, đôi khi validate trực tiếp từ JSON đơn giản hơn:

```java
public Result create(Http.Request request) {
    JsonNode body = request.body().asJson();

    // Manual validation
    List<String> errors = new ArrayList<>();

    if (body == null) {
        return badRequest(Json.newObject().put("error", "JSON body required"));
    }

    String title = Optional.ofNullable(body.get("title"))
        .map(JsonNode::asText)
        .orElse("").trim();

    if (title.isEmpty()) errors.add("title: required");
    if (title.length() > 255) errors.add("title: max 255 characters");

    if (!errors.isEmpty()) {
        ObjectNode errorResponse = Json.newObject();
        errorResponse.put("status", "validation_error");
        errorResponse.putPOJO("errors", errors);
        return badRequest(errorResponse);
    }

    // Proceed
    Todo todo = repository.save(title);
    return created(Json.toJson(todo));
}
```

---

## 6. Cross-Field Validation với `@Validate`

Khi cần validate liên quan đến nhiều field:

```java
import play.data.validation.Constraints.Validate;
import play.data.validation.Constraints.Validatable;

@Validate
public class PasswordChangeForm implements Validatable<String> {
    @Constraints.Required
    public String newPassword;

    @Constraints.Required
    public String confirmPassword;

    @Override
    public String validate() {
        if (!newPassword.equals(confirmPassword)) {
            return "Passwords do not match";
        }
        if (newPassword.length() < 8) {
            return "Password must be at least 8 characters";
        }
        return null;  // null = valid
    }
}

// Hoặc trả ValidationError để gắn lỗi vào field cụ thể
@Validate
public class RegistrationForm implements Validatable<ValidationError> {
    public String username;
    public String email;

    @Override
    public ValidationError validate() {
        if (username != null && username.equals(email)) {
            return new ValidationError("username", "Username cannot be the same as email");
        }
        return null;
    }
}
```

---

## 7. Validation Groups (Partial Validation)

Validate chỉ một phần constraints - hữu ích khi cùng class dùng cho nhiều operation:

```java
// Định nghĩa groups
public interface SignUpCheck {}
public interface UpdateCheck {}

public class UserForm {
    @Constraints.Required(groups = SignUpCheck.class)
    public String email;

    @Constraints.Required(groups = {SignUpCheck.class, UpdateCheck.class})
    public String name;

    // Chỉ required khi sign up, không required khi update
    @Constraints.Required(groups = SignUpCheck.class)
    public String password;
}

// Controller - chỉ validate SignUpCheck group
public Result register(Http.Request request) {
    Form<UserForm> form = formFactory
        .form(UserForm.class, SignUpCheck.class)  // Chỉ check group này
        .bindFromRequest(request);

    if (form.hasErrors()) {
        return badRequest(form.errorsAsJson());
    }
    // ...
}
```

---

## 8. Form Fill (Prepopulate Cho Edit)

```java
// Controller edit - fill form với data hiện tại
public Result edit(Http.Request request, Long id) {
    User user = userRepo.findById(id);

    UserForm formData = new UserForm();
    formData.name = user.getName();
    formData.email = user.getEmail();

    // fill() tạo form mới với data được điền sẵn
    Form<UserForm> filledForm = formFactory
        .form(UserForm.class)
        .fill(formData);

    return ok(views.html.user.edit.render(filledForm));
}
```

---

## 9. DynamicForm - Form Không Biết Trước Fields

```java
import play.data.DynamicForm;

public Result handleDynamic(Http.Request request) {
    DynamicForm dynamicForm = formFactory.form().bindFromRequest(request);

    // Lấy value theo tên field
    String username = dynamicForm.get("username");
    String email = dynamicForm.get("email");

    // Kiểm tra lỗi
    if (dynamicForm.hasErrors()) {
        return badRequest(dynamicForm.errorsAsJson());
    }

    return ok("Got: " + username + ", " + email);
}
```

---

## 10. Bài Tập

Thêm validation vào Todo API từ Day 07/14:

1. `title`: required, max 255 chars, không được chứa HTML tags
2. `done`: optional, default false
3. Validate `id` path param: phải > 0

Test:
```bash
# Thiếu title
curl -X POST http://localhost:9000/todos \
  -H "Content-Type: application/json" \
  -d '{}'
# → 400 {"errors": {"title": ["error.required"]}}

# Title quá dài
curl -X POST http://localhost:9000/todos \
  -H "Content-Type: application/json" \
  -d '{"title": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa..."}'
# → 400 validation error
```
