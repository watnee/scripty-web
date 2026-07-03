<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Project Profile</title>
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
                    <li aria-current="page">${viewModel.title}</li>
                </ol>
            </nav>
            <h1>${viewModel.title} <small><a href="${pageContext.request.contextPath}/project/edit?id=${viewModel.id}" role="button">edit</a> <a href="${pageContext.request.contextPath}/project/delete?id=${viewModel.id}" role="button" _="on click if not confirm('Are you sure you want to delete this project?') halt">delete</a></small></h1>
            <div class="f-row" style="gap: 2rem; align-items: start">
                <div style="flex: 3">
                    <h2>Scenes <small><a href="${pageContext.request.contextPath}/scene/all?projectId=${viewModel.id}" role="button">View All</a></small></h2>
                    <c:forEach items="${viewModel.scenes}" var="scene" varStatus="loop">
                        <h3 style="text-transform: uppercase; margin-bottom: 0.25em">
                            <a href="${pageContext.request.contextPath}/scene/show?id=${scene.id}" style="color: inherit; text-decoration: none">${empty scene.name || scene.name.trim().isEmpty() ? 'Untitled Scene' : scene.name}</a>
                        </h3>
                        <table class="table-blocks-all" id="table-blocks-${scene.id}">
                            <c:forEach items="${scene.blocks}" var="block">
                                <tr draggable="true" data-block-id="${block.id}" data-block-order="${block.order}">
                                    <td class="block-left-controls">
                                        <a hx-get="${pageContext.request.contextPath}/block/createBelowInline?id=${block.id}" hx-target="closest tr" hx-swap="afterend" hx-boost="false" role="button" class="create-below">+</a>
                                        <span class="drag-handle">&#8942;&#8942;</span>
                                    </td>
                                    <td class="block-content" hx-get="${pageContext.request.contextPath}/block/editInline?id=${block.id}" hx-trigger="click[!event.target.closest('a')&&!event.target.closest('form')]" hx-swap="innerHTML" style="cursor: pointer">
                                        <c:if test="${not empty block.personName}">
                                            <p style="margin-bottom: 0; text-align: center">
                                                <a href="${pageContext.request.contextPath}/character/show?id=${block.personId}" class="character-name" style="text-transform: uppercase">${block.personName}</a>
                                            </p>
                                        </c:if>
                                        ${block.content}
                                    </td>
                                </tr>
                            </c:forEach>
                            <tr hx-get="${pageContext.request.contextPath}/block/createInline?sceneId=${scene.id}" hx-trigger="load" hx-swap="outerHTML"></tr>
                        </table>
                    </c:forEach>
                    <form action="${pageContext.request.contextPath}/scene/createAndReturn" method="POST" hx-boost="false" style="display:inline">
                        <input type="hidden" name="projectId" value="${viewModel.id}" />
                        <button type="submit" role="button">Create New Scene</button>
                    </form>
                </div>
                <aside style="flex: 1">
                    <p><a href="${pageContext.request.contextPath}/character/list?projectId=${viewModel.id}" role="button">Characters</a></p>
                </aside>
            </div>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
        <script>
        document.querySelectorAll('.table-blocks-all').forEach(function(table) {
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
                    var params = new URLSearchParams();
                    params.append('id', blockId);
                    params.append('position', targetOrder);
                    fetch('${pageContext.request.contextPath}/block/moveTo', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                        body: params.toString(),
                        redirect: 'manual'
                    }).then(function() { window.location.reload(); });
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
        });
        </script>
    </body>
</html>
