<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Scene Profile</title>
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
                    <li aria-current="page" style="text-transform: uppercase">${viewModel.name}</li>
                </ol>
            </nav>
            <h1><span class="scene-name-wrap"><span class="scene-name-display" hx-get="${pageContext.request.contextPath}/scene/editNameInline?id=${viewModel.id}" hx-trigger="click" hx-target="closest .scene-name-wrap" hx-swap="innerHTML" style="cursor: pointer; text-transform: uppercase; min-width: 10em; display: inline-block"><c:choose><c:when test="${empty viewModel.name || viewModel.name.trim().isEmpty()}"><span style="color: #ccc; font-style: italic">click to name scene</span></c:when><c:otherwise>${viewModel.name}</c:otherwise></c:choose></span></span> <small><a href="${pageContext.request.contextPath}/scene/delete?id=${viewModel.id}" role="button" _="on click if not confirm('Are you sure you want to delete this scene?') halt">delete</a></small></h1>
            <table id="table-blocks">
                <c:forEach items="${viewModel.blocks}" var="block" varStatus="loop">
                    <tr draggable="true" data-block-id="${block.id}" data-block-order="${block.order}">
                        <td class="block-left-controls">
                            <a hx-get="${pageContext.request.contextPath}/block/createBelowInline?id=${block.id}" hx-target="closest tr" hx-swap="afterend" hx-boost="false" class="create-below" role="button">+</a>
                            <span class="drag-handle" title="Drag to reorder">&#8942;&#8942;</span>
                        </td>
                        <td class="block-content" hx-get="${pageContext.request.contextPath}/block/editInline?id=${block.id}" hx-trigger="click[!event.target.closest('a')&&!event.target.closest('form')]" hx-swap="innerHTML">
                            <c:if test="${not empty block.personName}">
                                <p style="margin-bottom: 0; text-align: center">
                                    <a href="${pageContext.request.contextPath}/character/show?id=${block.personId}" class="character-name" style="text-transform: uppercase">${block.personName}</a>
                                </p>
                            </c:if>
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
                            </span>
                        </td>
                    </tr>
                </c:forEach>
                <tr hx-get="${pageContext.request.contextPath}/block/createInline?sceneId=${viewModel.id}" hx-trigger="load" hx-swap="outerHTML"></tr>
            </table>
            <p>
<a id="create-scene-btn" hx-get="${pageContext.request.contextPath}/scene/createBelowInline?id=${viewModel.id}" hx-target="#create-scene-btn" hx-swap="afterend" role="button" _="on htmx:afterSwap hide me">Create New Scene</a>
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
        <script>
        (function() {
            var table = document.getElementById('table-blocks');
            var dragRow = null;

            table.addEventListener('dragstart', function(e) {
                var tr = e.target.closest('tr');
                if (!tr || !table.contains(tr)) { e.preventDefault(); return; }
                dragRow = tr;
                tr.classList.add('dragging');
                e.dataTransfer.effectAllowed = 'move';
            });

            table.addEventListener('dragend', function() {
                if (dragRow) dragRow.classList.remove('dragging');
                table.querySelectorAll('.drag-over').forEach(function(el) { el.classList.remove('drag-over'); });
                dragRow = null;
            });

            table.addEventListener('dragover', function(e) {
                e.preventDefault();
                var tr = e.target.closest('tr');
                if (!tr || !table.contains(tr) || tr === dragRow) return;
                table.querySelectorAll('.drag-over').forEach(function(el) { el.classList.remove('drag-over'); });
                tr.classList.add('drag-over');
            });

            table.addEventListener('dragleave', function(e) {
                var tr = e.target.closest('tr');
                if (tr) tr.classList.remove('drag-over');
            });

            table.addEventListener('drop', function(e) {
                e.preventDefault();
                var targetRow = e.target.closest('tr');
                if (!targetRow || !table.contains(targetRow) || !dragRow || targetRow === dragRow) return;
                var blockId = dragRow.getAttribute('data-block-id');
                var targetOrder = targetRow.getAttribute('data-block-order');
                if (blockId && targetOrder) {
                    var form = document.createElement('form');
                    form.method = 'POST';
                    form.action = '${pageContext.request.contextPath}/block/moveTo';
                    form.innerHTML = '<input type="hidden" name="id" value="' + blockId + '">' +
                                     '<input type="hidden" name="position" value="' + targetOrder + '">';
                    document.body.appendChild(form);
                    form.submit();
                } else {
                    var rect = targetRow.getBoundingClientRect();
                    var mid = rect.top + rect.height / 2;
                    if (e.clientY < mid) {
                        targetRow.parentNode.insertBefore(dragRow, targetRow);
                    } else {
                        targetRow.parentNode.insertBefore(dragRow, targetRow.nextSibling);
                    }
                }
            });
        })();
        </script>
    </body>
</html>
