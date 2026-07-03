(function () {
    var dropdown = document.getElementById('align-dropdown');
    if (!dropdown) return;

    var toggleBtn = dropdown.querySelector('.nav-dropdown-toggle');
    if (!toggleBtn) return;

    toggleBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        var isOpen = dropdown.classList.contains('open');
        document.querySelectorAll('.nav-dropdown').forEach(function (d) { d.classList.remove('open'); });
        toggleBtn.setAttribute('aria-expanded', String(!isOpen));
        if (!isOpen) dropdown.classList.add('open');
    });

    document.addEventListener('click', function (e) {
        if (!dropdown.contains(e.target)) {
            dropdown.classList.remove('open');
            toggleBtn.setAttribute('aria-expanded', 'false');
        }
    });

    dropdown.querySelectorAll('.align-option').forEach(function (option) {
        option.addEventListener('click', function () {
            dropdown.classList.remove('open');
            toggleBtn.setAttribute('aria-expanded', 'false');

            var checkboxes = document.querySelectorAll('.block-select-checkbox:checked');
            if (checkboxes.length === 0) {
                alert('Please select at least one block.');
                return;
            }
            var ids = Array.from(checkboxes).map(function (cb) { return cb.value; });

            var form = document.createElement('form');
            form.method = 'POST';
            form.action = dropdown.getAttribute('data-action') || '/block/bulkAlign';

            function addInput(name, value) {
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = name;
                input.value = value;
                form.appendChild(input);
            }

            addInput('ids', ids.join(','));
            addInput('alignment', option.getAttribute('data-align'));
            if (dropdown.getAttribute('data-project-id')) {
                addInput('projectId', dropdown.getAttribute('data-project-id'));
            }
            if (dropdown.getAttribute('data-scene-id')) {
                addInput('sceneId', dropdown.getAttribute('data-scene-id'));
            }

            document.body.appendChild(form);
            form.submit();
        });
    });
})();
