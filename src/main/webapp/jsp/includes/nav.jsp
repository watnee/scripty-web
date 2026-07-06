<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<header>
    <nav>
        <a href="${pageContext.request.contextPath}/"><strong>Scripty</strong></a>
        <c:if test="${pageContext.request.userPrincipal != null}">
            <a href="${pageContext.request.contextPath}/project/list" ${param.page == 'projects' ? 'aria-current="page"' : ''}>Projects</a>
        </c:if>
    </nav>
</header>