class DepositionAssociations {

    static endpoint = `${this.getProxyPath()}api/v1/submissions/${submissionId}/associations`;

    static getAssociationsData(pageNumber) {
        let pageSize = this.getSelectedPageSize();
        const URI = `${this.endpoint}?page=${--pageNumber}&size=${pageSize}`;
        $('#deposition-associations-data').html(UI.loadingIcon());
        this.getData(URI);
    }

    static getData(url) {
        let httpRequest = HttpRequestEngine.requestWithoutBody(url, 'GET');
        HttpRequestEngine.fetchRequest(httpRequest).then((data) => {
            UI.removeChildren('deposition-associations-data');
            let columns = ['study_tag','pvalue','pvalue_text','effect_allele','effect_allele_frequency','other_allele',
                'haplotype_id','variant_id','standard_error','odds_ratio','ci_lower','ci_upper','beta','beta_direction','beta_unit',
                'proxy_variant'];
            data._embedded.associations.map(dData => {
                DepositionAssociations.updateDataRows(dData, dData.study_tag, columns, 'deposition-associations-data');
            });
            DepositionAssociations.generatePagination(data.page);
        });
    }

    static updateDataRows(dataObject, dataId, properties, tableBodyId) {
        const baseURL = this.endpoint;
        const tableRows = document.querySelector(`#${tableBodyId}`);
        const tr = document.createElement('tr');
        tr.setAttribute('id', `row-${dataId}`);
        properties.forEach((property, index, prop) => {
            let td = UI.tableData(dataObject[property], `col-${index}-${dataId}`);
            tr.appendChild(td);
        });
        tableRows.prepend(tr);
    }

    static generatePagination(pageData) {

        let totalPages = pageData.totalPages;
        let totalElements = pageData.totalElements;
        let page = pageData.number + 1;
        let currentIndex = pageData.number + 1;
        let beginIndex = Math.max(1, page - 4);
        let endIndex = Math.min(beginIndex + 7, Math.max(1, totalPages));

        let pageList = document.createElement('ul');
        pageList.setAttribute('class', 'pagination pagination-sm');

        // First Pagination Link
        let firstPage = document.createElement("li");
        let firstLink = document.createElement("a");
        let firstText = document.createTextNode('First «');
        if (currentIndex === 1) {
            firstPage.setAttribute('class', 'disabled');
        } else {
            firstLink.style.cursor = "pointer";
            firstLink.addEventListener("click", () => {
                DepositionAssociations.getAssociationsData(1);
            });
        }
        firstLink.appendChild(firstText);
        firstPage.appendChild(firstLink);
        pageList.appendChild(firstPage);

        // Next Pagination Links:
        for (let i = beginIndex; i < endIndex; i++) {
            let nextPage = document.createElement("li");
            let nextLink = document.createElement("a");
            let nextPageText = document.createTextNode(i);
            if (i === page) {
                nextPage.setAttribute('class', 'active');
            } else {
                nextLink.style.cursor = "pointer";
                nextLink.addEventListener("click", () => {
                    DepositionAssociations.getAssociationsData(i);
                });
            }
            nextLink.appendChild(nextPageText);
            nextPage.appendChild(nextLink);
            pageList.appendChild(nextPage);
        }

        // Last Pagination Link:
        let finalPage = document.createElement("li");
        let finalPageLink = document.createElement("a");
        finalPageLink.style.cursor = "pointer";
        //finalPageLink.setAttribute('id', pageId);
        let finalPageText = document.createTextNode('Last «');
        if (page === totalPages) {
            finalPage.setAttribute('class', 'disabled');
        } else {
            finalPageLink.addEventListener("click", () => {
                DepositionAssociations.getAssociationsData(totalPages);
            });
        }
        finalPageLink.appendChild(finalPageText);
        finalPage.appendChild(finalPageLink);
        pageList.appendChild(finalPage);

        let report = document.createElement("li");
        let reportLink = document.createElement("a");
        let reportText = document.createTextNode(this.pageReport(pageData));
        reportLink.appendChild(reportText);
        report.appendChild(reportLink);
        pageList.appendChild(report);

        UI.removeChildren('page-area-associations');
        const dPagination = document.querySelector('#page-area-associations');
        dPagination.appendChild(pageList);
    }

    static pageSizeConfig() {
        let pageSizes = [5, 10, 25, 50, 100, 250, 500];
        let pageSizeSelect = UI.dropDownSelect(pageSizes)
        pageSizeSelect.addEventListener("change", (event) => {
            localStorage.setItem('page_size', event.target.value);
            DepositionAssociations.getAssociationsData(1);
        });
        UI.removeChildren('page-size-area-associations');
        const pageSizeArea = document.querySelector('#page-size-area-associations');
        pageSizeArea.appendChild(pageSizeSelect);
        pageSizeSelect.value = localStorage.getItem('page_size');
    }

    static getSelectedPageSize() {
        let selectedSize = localStorage.getItem('page_size');
        let pageSize = (selectedSize == null) ? 10 : selectedSize;
        localStorage.setItem('page_size', pageSize);
        return pageSize;
    }

    static pageReport(pageData) {
        let page = pageData.number + 1;
        let size = this.getSelectedPageSize();
        let from = pageData.number * size + 1;
        let to = page * size;
        if (page === pageData.totalPages) {
            to = pageData.totalElements
        }
        return `${from} to ${to} of ${pageData.totalElements} rows`
    }

    static getProxyPath() {
        return  window.location.href.split("submissions")[0];
    }

    static init(){
        DepositionAssociations.getAssociationsData(1);
        DepositionAssociations.pageSizeConfig();
    }

}
DepositionAssociations.init();
