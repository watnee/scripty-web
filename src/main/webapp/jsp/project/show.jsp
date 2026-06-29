<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Project Profile</title>
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
                    <li aria-current="page">${viewModel.title}</li>
                </ol>
            </nav>
            <h1>${viewModel.title} <small><a href="${pageContext.request.contextPath}/project/edit?id=${viewModel.id}" role="button">edit</a> <a href="${pageContext.request.contextPath}/project/delete?id=${viewModel.id}" role="button" _="on click if not confirm('Are you sure you want to delete this project?') halt">delete</a></small></h1>
            <div class="f-row" style="gap: 2rem; align-items: start">
                <div style="flex: 3">
                    <h2>Scenes</h2>
                    <table id="table-scenes">
                        <c:forEach items="${viewModel.scenes}" var="scene" varStatus="loop">
                            <tr>
                                <td><a href="${pageContext.request.contextPath}/scene/show?id=${scene.id}" style="text-transform: uppercase">${scene.name}</a></td>
                                <td>
                                    <span class="nowrap">
                                        <c:if test="${not loop.last}">
                                            <a href="${pageContext.request.contextPath}/scene/moveDown?id=${scene.id}" class="move-down" role="button">&#8595;</a>
                                        </c:if>
                                        <c:if test="${not loop.first}">
                                            <a href="${pageContext.request.contextPath}/scene/moveUp?id=${scene.id}" class="move-up" role="button">&#8593;</a>
                                        </c:if>
                                    </span>
                                </td>
                            </tr>
                        </c:forEach>
                    </table>
                    <p><a href="${pageContext.request.contextPath}/scene/create?projectId=${viewModel.id}" role="button">Create New Scene</a></p>
                </div>
                <aside style="flex: 1">
                    <c:if test="${not empty viewModel.persons}">
                        <details open>
                            <summary><strong>Characters</strong></summary>
                            <ul>
                            <c:forEach items="${viewModel.persons}" var="character">
                                <li><a href="${pageContext.request.contextPath}/character/show?id=${character.id}">${character.name}</a></li>
                            </c:forEach>
                            </ul>
                        </details>
                    </c:if>
                    <p><a href="${pageContext.request.contextPath}/character/create?projectId=${viewModel.id}" role="button">Create New Character</a></p>
                </aside>
            </div>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
    </body>
</html>
