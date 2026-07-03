<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Character Profile</title>
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
                    <li><a href="${pageContext.request.contextPath}/project/list">Projects</a></li>
                    <li><a href="${pageContext.request.contextPath}/project/show?id=${viewModel.projectId}">${viewModel.projectTitle}</a></li>
                    <li aria-current="page">${viewModel.name}</li>
                </ol>
            </nav>
            <h1>${viewModel.name} <small><a href="${pageContext.request.contextPath}/character/edit?id=${viewModel.id}" role="button">edit</a> <a href="${pageContext.request.contextPath}/character/delete?id=${viewModel.id}" role="button" _="on click if not confirm('Are you sure you want to delete this character?') halt">delete</a></small></h1>
            <p>Full Name: ${viewModel.fullName}</p>
            <c:if test="${not empty viewModel.actorName}">
                <p>Actor: <a href="${pageContext.request.contextPath}/actor/show?id=${viewModel.actorId}">${viewModel.actorName}</a></p>
            </c:if>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
    </body>
</html>
