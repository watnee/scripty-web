<%@ taglib prefix="sf" uri="http://www.springframework.org/tags/form" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Create New Block</title>
        <link rel="stylesheet" href="https://unpkg.com/missing.css@1.1.3/dist/missing.min.css">
        <link href="${pageContext.request.contextPath}/css/scripty.css" rel="stylesheet">
        <link rel="shortcut icon" href="${pageContext.request.contextPath}/favicon.ico" type="image/x-icon">
    </head>
    <body hx-boost="true">
        <jsp:include page="../includes/nav.jsp" />
        <main>
            <jsp:include page="../includes/logout.jsp" />
            <h1>Create New Block</h1>
            <sf:form action="${pageContext.request.contextPath}/block/createBelow" method="post" modelAttribute="commandModel">
                <sf:hidden path="id" />
                <label>
                    Content
                    <sf:textarea path="content" rows="25" cols="30" />
                    <sf:errors path="content" />
                </label>
                <label>
                    Character
                    <sf:select path="personId">
                        <sf:option value="" label="No character" />
                        <sf:options items="${viewModel.persons}" itemValue="id" itemLabel="name" />
                    </sf:select>
                    <sf:errors path="personId" />
                </label>
                <p>
                    <a href="${pageContext.request.contextPath}/scene/show?id=${viewModel.sceneId}" role="button">Cancel</a>
                    <button type="submit">Submit</button>
                </p>
            </sf:form>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
    </body>
</html>
