(function () {
    var importBtn = document.getElementById('nav-import');
    var importFile = document.getElementById('project-import-file');
    var importForm = document.getElementById('project-import-form');

    if (!importBtn || !importFile || !importForm) {
        return;
    }

    importBtn.addEventListener('click', function () {
        importFile.click();
    });

    importFile.addEventListener('change', function () {
        if (!importFile.files || importFile.files.length === 0) {
            return;
        }

        if (!window.confirm('Import will replace all blocks in this project. Continue?')) {
            importFile.value = '';
            return;
        }

        importForm.submit();
    });
})();
