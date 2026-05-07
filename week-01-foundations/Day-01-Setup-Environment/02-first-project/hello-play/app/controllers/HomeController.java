package controllers;

import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

/**
 * Day 01 - Controller đầu tiên
 *
 * Controller trong Play là class thường, không cần annotation đặc biệt.
 * Mỗi public method trả về Result = 1 HTTP action.
 */
public class HomeController extends Controller {

    /**
     * Action cho GET /
     *
     * ok() = HTTP 200 OK
     * Nội dung có thể là String, JSON, HTML template, binary, v.v.
     */
    public Result index() {
        return ok("Hello, Play Framework 3!");
    }

    /**
     * Action cho GET /hello
     *
     * Bài tập Day 01: Thêm route này vào conf/routes và chạy thử.
     */
    public Result hello() {
        return ok("World! (từ route /hello)");
    }

    /**
     * Demo các loại Result khác nhau
     * Uncomment từng dòng để thử nghiệm
     */
    public Result allResultTypes(Http.Request request) {
        // 200 OK với text
        return ok("200 OK");

        // 200 OK với JSON
        // return ok(play.libs.Json.newObject().put("message", "OK"));

        // 201 Created
        // return created("Resource created");

        // 204 No Content
        // return noContent();

        // 400 Bad Request
        // return badRequest("Invalid input");

        // 401 Unauthorized
        // return unauthorized("Login required");

        // 403 Forbidden
        // return forbidden("Not allowed");

        // 404 Not Found
        // return notFound("Resource not found");

        // 500 Internal Server Error
        // return internalServerError("Something went wrong");

        // Custom status code
        // return status(418, "I'm a teapot");

        // Redirect
        // return redirect("/hello");

        // Redirect với flash message
        // return redirect("/").flashing("success", "Done!");
    }
}
