<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - All Scenes</title>
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
                    <li aria-current="page">All Scenes</li>
                </ol>
            </nav>
            <h1>${viewModel.projectTitle} <small>All Scenes</small></h1>
            <c:forEach items="${viewModel.scenes}" var="scene">
                <h2 style="text-transform: uppercase"><a href="${pageContext.request.contextPath}/scene/show?id=${scene.id}">${scene.name}</a> <small><a href="${pageContext.request.contextPath}/scene/show?id=${scene.id}" role="button">view</a></small></h2>
                <table class="table-blocks-all">
                    <c:forEach items="${scene.blocks}" var="block">
                        <tr>
                            <td class="block-content">
                                <c:if test="${not empty block.personName}">
                                    <p style="margin-bottom: 0; text-align: center">
                                        <a href="${pageContext.request.contextPath}/character/show?id=${block.personId}" class="character-name" style="text-transform: uppercase">${block.personName}</a>
                                    </p>
                                </c:if>
                                <c:if test="${not empty block.content}">
                                    <p style="white-space: pre-wrap">${block.content}</p>
                                </c:if>
                            </td>
                        </tr>
                    </c:forEach>
                </table>
            </c:forEach>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
    </body>
</html>
