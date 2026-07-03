<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Scripty - Delete Project</title>
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
                    <li><a href="${pageContext.request.contextPath}/project/show?id=${viewModel.id}"><c:out value="${viewModel.title}"/></a></li>
                    <li aria-current="page">Delete</li>
                </ol>
            </nav>

            <div class="box bad" style="margin-bottom: 2rem; border-left: 5px solid var(--bad-fg, #d32f2f); padding: 1.5rem; background: var(--bg-bad, #fde8e8); border-radius: 6px;">
                <h2 style="color: var(--bad-fg, #d32f2f); margin-top: 0;">⚠️ Danger Zone: Delete Project</h2>
                <p>You are about to delete the project <strong id="project-title-to-match"><c:out value="${viewModel.title}"/></strong>.</p>
                <p style="font-weight: bold;">This action is permanent and cannot be undone.</p>
                <p>Deleting this project will permanently remove:</p>
                <ul>
                    <li>All scenes and blocks</li>
                    <li>All character profiles and association data</li>
                    <li>All auto-save history and versions</li>
                </ul>
            </div>

            <form action="${pageContext.request.contextPath}/project/delete" method="post" style="max-width: 500px;">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <input type="hidden" name="id" value="${viewModel.id}" />
                
                <p>
                    To confirm deletion, please type the project name: 
                    <strong style="user-select: all;"><c:out value="${viewModel.title}"/></strong>
                </p>
                
                <label style="display: block; margin-bottom: 1.5rem;">
                    <input type="text" id="confirm-title-input" placeholder="Type project name here" autocomplete="off" style="width: 100%; box-sizing: border-box;" />
                </label>
                
                <p style="display: flex; gap: 1rem; align-items: center;">
                    <a href="${pageContext.request.contextPath}/project/show?id=${viewModel.id}" role="button" class="secondary">Cancel</a>
                    <button type="submit" id="delete-submit-btn" class="bad" disabled style="background-color: var(--bad-fg, #d32f2f); color: white;">Permanently Delete Project</button>
                </p>
            </form>
        </main>
        <script src="https://unpkg.com/htmx.org@2.0.4"></script>
        <script src="${pageContext.request.contextPath}/js/_hyperscript.min.js"></script>
        <script>
        (function() {
            const projectTitleEl = document.getElementById('project-title-to-match');
            const projectTitle = projectTitleEl ? projectTitleEl.textContent : '';
            const input = document.getElementById('confirm-title-input');
            const btn = document.getElementById('delete-submit-btn');
            if (input && btn) {
                input.addEventListener('input', function() {
                    if (input.value.trim() === projectTitle.trim()) {
                        btn.removeAttribute('disabled');
                    } else {
                        btn.setAttribute('disabled', 'true');
                    }
                });
            }
        })();
        </script>
    </body>
</html>
