<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Login</title>
        <link rel="stylesheet" href="https://unpkg.com/missing.css@1.1.3/dist/missing.min.css">
        <link href="${pageContext.request.contextPath}/css/scripty.css" rel="stylesheet">
        <link rel="shortcut icon" href="${pageContext.request.contextPath}/favicon.ico" type="image/x-icon">
    </head>
    <body hx-boost="true">
        <jsp:include page="includes/nav.jsp" />
        <main class="home">
            <h1>Scripty</h1>
            <c:if test="${param.login_error == 1}">
                <p>Incorrect Username or Password.</p>
            </c:if>
            <form method="POST" action="j_spring_security_check" hx-boost="false">
                <label>
                    Username
                    <input type="text" name="j_username" placeholder="Username" />
                </label>
                <label>
                    Password
                    <input type="password" name="j_password" placeholder="Password" />
                </label>
                <button type="submit">Sign In</button>
            </form>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
    </body>
</html>
