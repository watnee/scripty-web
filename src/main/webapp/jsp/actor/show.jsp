<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Actor Profile</title>
        <link rel="stylesheet" href="https://unpkg.com/missing.css@1.1.3/dist/missing.min.css">
        <link href="${pageContext.request.contextPath}/css/scripty.css" rel="stylesheet">
        <link rel="shortcut icon" href="${pageContext.request.contextPath}/favicon.ico" type="image/x-icon">
    </head>
    <body hx-boost="true">
        <jsp:include page="../includes/nav.jsp" />
        <main>
            <jsp:include page="../includes/logout.jsp" />
            <nav aria-label="Breadcrumb">
                <ol>
                    <li><a href="${pageContext.request.contextPath}/actor/list">Casting</a></li>
                    <li aria-current="page">${viewModel.first} ${viewModel.last}</li>
                </ol>
            </nav>
            <h1>${viewModel.first} ${viewModel.last} <small><a href="${pageContext.request.contextPath}/actor/edit?id=${viewModel.id}" role="button">edit</a> <a href="${pageContext.request.contextPath}/actor/delete?id=${viewModel.id}" role="button" _="on click if not confirm('Are you sure you want to delete this actor?') halt">delete</a></small></h1>
            <p>Phone: ${viewModel.phone}</p>
            <p>Email: ${viewModel.email}</p>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
    </body>
</html>
