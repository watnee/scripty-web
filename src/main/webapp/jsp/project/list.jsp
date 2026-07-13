<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Projects</title>
        <link rel="stylesheet" href="https://unpkg.com/missing.css@1.1.3/dist/missing.min.css">
        <link href="${pageContext.request.contextPath}/css/scripty.css" rel="stylesheet">
        <link rel="shortcut icon" href="${pageContext.request.contextPath}/favicon.ico" type="image/x-icon">
    </head>
    <body hx-boost="true">
        <jsp:include page="../includes/nav.jsp">
            <jsp:param name="page" value="projects" />
        </jsp:include>
        <main>
            <jsp:include page="../includes/logout.jsp" />
            <nav aria-label="Breadcrumb">
                <ol>
                    <li aria-current="page">Projects</li>
                </ol>
            </nav>
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; flex-wrap: wrap; gap: 0.5rem;">
                <h1 style="margin: 0;">Projects</h1>
                <a href="${pageContext.request.contextPath}/project/create" role="button" style="margin: 0;">New Project</a>
            </div>
            <table id="table-projects">
                <c:forEach items="${viewModel.projects}" var="project">
                    <tr>
                        <td><a href="${pageContext.request.contextPath}/project/show?id=${project.id}">${project.title}</a></td>
                    </tr>
                </c:forEach>
            </table>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
    </body>
</html>
