<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Scene Profile</title>
        <link rel="stylesheet" href="https://unpkg.com/missing.css@1.1.3/dist/missing.min.css">
        <link href="${pageContext.request.contextPath}/css/martinis.css" rel="stylesheet">
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
                    <li aria-current="page" style="text-transform: uppercase">${viewModel.name}</li>
                </ol>
            </nav>
            <h1 style="text-transform: uppercase">${viewModel.name} <small><a href="${pageContext.request.contextPath}/scene/edit?id=${viewModel.id}" role="button">edit</a> <a href="${pageContext.request.contextPath}/scene/delete?id=${viewModel.id}" role="button" _="on click if not confirm('Are you sure you want to delete this scene?') halt">delete</a></small></h1>
            <table id="table-blocks">
                <c:forEach items="${viewModel.blocks}" var="block" varStatus="loop">
                    <tr>
                        <td>
                            <c:choose>
                                <c:when test="${not empty block.personName}">
                                    <p style="margin-bottom: 0; text-align: center">
                                        <a href="${pageContext.request.contextPath}/character/show?id=${block.personId}" class="character-name" style="text-transform: uppercase">${block.personName}</a>
                                    </p>
                                    <div style="text-align: center">
                                        ${block.content}
                                    </div>
                                </c:when>
                                <c:otherwise>
                                    ${block.content}
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <span class="nowrap">
                                <a href="${pageContext.request.contextPath}/block/edit?id=${block.id}" role="button">edit</a>
                                <a href="${pageContext.request.contextPath}/block/delete?id=${block.id}" role="button" _="on click if not confirm('Are you sure you want to delete this block?') halt">delete</a>
                                <c:if test="${not loop.last}">
                                    <a href="${pageContext.request.contextPath}/block/moveDown?id=${block.id}" class="move-down" role="button">&#8595;</a>
                                </c:if>
                                <c:if test="${not loop.first}">
                                    <a href="${pageContext.request.contextPath}/block/moveUp?id=${block.id}" class="move-up" role="button">&#8593;</a>
                                </c:if>
                                <a href="${pageContext.request.contextPath}/block/createBelow?id=${block.id}" class="create-below" role="button">+ block</a>
                            </span>
                        </td>
                    </tr>
                </c:forEach>
            </table>
            <p>
                <a href="${pageContext.request.contextPath}/block/create?sceneId=${viewModel.id}" role="button">Create New Block</a>
                <a href="${pageContext.request.contextPath}/scene/createBelow?id=${viewModel.id}" role="button">Create New Scene</a>
                <a href="${pageContext.request.contextPath}/character/create?projectId=${viewModel.projectId}" role="button">Create New Character</a>
            </p>
            <nav aria-label="Scene navigation" class="scene-pager">
                <c:if test="${not empty viewModel.previousSceneName}">
                    <a href="${pageContext.request.contextPath}/scene/show?id=${viewModel.previousSceneId}" title="${viewModel.previousSceneName}">&larr; Previous Scene</a>
                </c:if>
                <c:if test="${not empty viewModel.nextSceneName}">
                    <a href="${pageContext.request.contextPath}/scene/show?id=${viewModel.nextSceneId}" title="${viewModel.nextSceneName}" style="margin-left: auto">Next Scene &rarr;</a>
                </c:if>
            </nav>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
    </body>
</html>
