<%@ taglib prefix="sf" uri="http://www.springframework.org/tags/form" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Create New Scene</title>
        <link rel="stylesheet" href="https://unpkg.com/missing.css@1.1.3/dist/missing.min.css">
        <link href="${pageContext.request.contextPath}/css/scripty.css" rel="stylesheet">
        <link rel="shortcut icon" href="${pageContext.request.contextPath}/favicon.ico" type="image/x-icon">
    </head>
    <body hx-boost="true">
        <jsp:include page="../includes/nav.jsp" />
        <main>
            <jsp:include page="../includes/logout.jsp" />
            <h1>Create New Scene</h1>
            <sf:form action="${pageContext.request.contextPath}/scene/create" method="post" modelAttribute="commandModel">
                <sf:hidden path="projectId" />
                <label>
                    Name
                    <sf:input type="text" spellcheck="true" path="name" />
                    <sf:errors path="name" />
                </label>
                <p>
                    <a href="${pageContext.request.contextPath}/project/show?id=${viewModel.projectId}" role="button">Cancel</a>
                    <button type="submit">Submit</button>
                </p>
            </sf:form>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
    </body>
</html>
