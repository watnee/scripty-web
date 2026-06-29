<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Casting</title>
        <link rel="stylesheet" href="https://unpkg.com/missing.css@1.1.3/dist/missing.min.css">
        <link href="${pageContext.request.contextPath}/css/scripty.css" rel="stylesheet">
        <link rel="shortcut icon" href="${pageContext.request.contextPath}/favicon.ico" type="image/x-icon">
    </head>
    <body hx-boost="true">
        <jsp:include page="../includes/nav.jsp">
            <jsp:param name="page" value="casting" />
        </jsp:include>
        <main>
            <jsp:include page="../includes/logout.jsp" />
            <nav aria-label="Breadcrumb">
                <ol>
                    <li aria-current="page">Casting</li>
                </ol>
            </nav>
            <h1>Casting</h1>
            <table id="table-actors">
                <c:forEach items="${viewModel.actors}" var="actor">
                    <tr>
                        <td><a href="${pageContext.request.contextPath}/actor/show?id=${actor.id}">${actor.first} ${actor.last}</a></td>
                    </tr>
                </c:forEach>
            </table>
            <p><a href="${pageContext.request.contextPath}/actor/create" role="button">Create New Actor</a></p>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
    </body>
</html>
