<header>
    <nav>
        <a href="${pageContext.request.contextPath}/"><strong>Scripty</strong></a>
        <a href="${pageContext.request.contextPath}/project/list" ${param.page == 'projects' ? 'aria-current="page"' : ''}>Projects</a>
        <a href="${pageContext.request.contextPath}/actor/list" ${param.page == 'casting' ? 'aria-current="page"' : ''}>Casting</a>
    </nav>
</header>