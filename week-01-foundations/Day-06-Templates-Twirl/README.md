# Day 06 - Twirl Templates (Server-Side Rendering)

## Mục tiêu
- Hiểu Twirl syntax cơ bản
- Biết khi nào nên/không nên dùng Twirl
- Template composition và helpers

---

## Khi Nào Dùng Twirl?

**Dùng Twirl khi:**
- Làm full-stack app (server renders HTML)
- Admin dashboard đơn giản
- Email templates
- App không cần SPA (single-page app)

**KHÔNG dùng Twirl khi:**
- Backend chỉ là REST API + frontend riêng (React/Vue/Angular)
- Đây là use case phổ biến nhất ngày nay

> Nếu bạn đang học Play để làm REST API backend + modern frontend → Twirl không quan trọng lắm. Xem qua để biết, không cần master.

---

## 1. Twirl Syntax Cơ Bản

Twirl là Scala template engine. File có đuôi `.scala.html`.

```html
@* app/views/greet.scala.html *@

@* Khai báo parameters - dòng đầu tiên bắt buộc *@
@(name: String, age: Int, items: List[String])

@* Đây là comment Twirl *@
<!-- Đây là comment HTML, vẫn xuất hiện trong output -->

<!DOCTYPE html>
<html>
<head><title>Hello @name</title></head>
<body>
    @* Interpolation: @ + biến *@
    <h1>Hello, @name!</h1>
    <p>Age: @age</p>

    @* Expression phức tạp: @() *@
    <p>Born in: @(2024 - age)</p>

    @* If/else *@
    @if(age >= 18) {
        <p>Adult</p>
    } else {
        <p>Minor</p>
    }

    @* For loop *@
    <ul>
    @for(item <- items) {
        <li>@item</li>
    }
    </ul>

    @* Match expression *@
    @age match {
        case a if a < 18 => { <span>Minor</span> }
        case a if a < 65 => { <span>Adult</span> }
        case _           => { <span>Senior</span> }
    }

    @* Gọi helper function *@
    @helper.input(myForm("email"))

    @* Include partial template *@
    @components.userCard(user)
</body>
</html>
```

**Escape HTML tự động**: Twirl tự escape `<`, `>`, `&` để tránh XSS.

```html
@* Hiển thị HTML thô (CẨNTHẬN: XSS risk!) *@
@Html("<b>Bold text</b>")
```

---

## 2. Gọi Template Từ Controller

```java
// Controller
public Result greet() {
    List<String> items = Arrays.asList("Apple", "Banana", "Cherry");
    // Play compile template thành class views.html.greet
    return ok(views.html.greet.render("Alice", 25, items));
}
```

**Type safety**: Nếu bạn truyền sai type (`Integer` thay vì `String`), compiler báo lỗi ngay.

---

## 3. Template Composition (Layout)

```html
@* app/views/main.scala.html - Layout chung *@
@(title: String)(content: Html)

<!DOCTYPE html>
<html>
<head>
    <title>@title - My App</title>
    <link rel="stylesheet" href="@routes.Assets.versioned("stylesheets/main.css")">
</head>
<body>
    <nav>
        <a href="@routes.HomeController.index()">Home</a>
        <a href="@routes.UserController.list()">Users</a>
    </nav>

    <main>
        @content
    </main>

    <footer>© 2024 My App</footer>
    <script src="@routes.Assets.versioned("javascripts/main.js")"></script>
</body>
</html>
```

```html
@* app/views/users/list.scala.html - Page cụ thể *@
@(users: List[User])

@* Sử dụng layout main - inject content vào *@
@main("Users") {
    <h1>User List</h1>
    <ul>
    @for(user <- users) {
        <li>
            <a href="@routes.UserController.show(user.id)">@user.name</a>
        </li>
    }
    </ul>
    <a href="@routes.UserController.create()">Add User</a>
}
```

---

## 4. Partial Templates (Components)

```html
@* app/views/components/userCard.scala.html *@
@(user: User, showEmail: Boolean = true)

<div class="user-card">
    <h3>@user.name</h3>
    @if(showEmail) {
        <p>@user.email</p>
    }
    <a href="@routes.UserController.show(user.id)">View Profile</a>
</div>
```

```html
@* Dùng component trong template khác *@
@for(user <- users) {
    @components.userCard(user)
    @components.userCard(user, showEmail = false)
}
```

---

## 5. Forms với Twirl

```html
@* app/views/todos/create.scala.html *@
@(form: Form[TodoForm])(implicit messages: Messages, request: RequestHeader)

@import helper._

@main("Create Todo") {
    @if(form.hasErrors) {
        <div class="errors">
            @form.errors.map { error =>
                <p>@error.key: @messages(error.message)</p>
            }
        </div>
    }

    @* CSRF token tự động được inject *@
    @helper.form(routes.TodoController.create()) {
        @CSRF.formField

        @inputText(form("title"), 'label -> "Title", 'placeholder -> "Enter todo title")
        @checkbox(form("done"), 'label -> "Done?")

        <button type="submit">Create</button>
    }
}
```

---

## 6. Reverse Routing trong Template

```html
@* Luôn dùng @routes.Controller.method() thay vì hardcode URL *@

@* ✅ ĐÚNG: Type-safe, tự cập nhật khi đổi routes *@
<a href="@routes.UserController.show(42L)">User</a>
<img src="@routes.Assets.versioned("images/logo.png")">
<link rel="stylesheet" href="@routes.Assets.versioned("stylesheets/app.css")">

@* ❌ SAI: Hardcode URL, dễ bị stale *@
<a href="/users/42">User</a>
```

---

## 7. Bài Tập (Nếu Muốn Thực Hành Twirl)

Xem `twirl-demo/` trong thư mục này:

Project có:
- Layout template `main.scala.html`
- Trang danh sách todos
- Form tạo todo
- Redirect-after-POST với flash message

```bash
cd twirl-demo
sbt run
# Mở http://localhost:9000
```

---

> **Lưu ý cho lộ trình này**: Từ Day 07 trở đi, chúng ta sẽ tập trung vào **REST API** (trả JSON, không dùng Twirl). Đây là hướng thực tế nhất với Play Framework hiện đại.
