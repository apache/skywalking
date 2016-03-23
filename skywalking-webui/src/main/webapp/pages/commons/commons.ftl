<#macro  navbar>
<nav class="navbar navbar-fixed-top navbar-default">
    <div>
        <#if loginUserInfo??>
            <p class="navbar-text pull-right">
            <div class="dropdown">
                <button class="btn btn-default dropdown-toggle" type="button" id="dropdownMenu1" data-toggle="dropdown"
                        aria-haspopup="true" aria-expanded="true">
                    Dropdown
                    <span class="caret"></span>
                </button>

            </div>
            </p>
        <#else >
            <p class="navbar-text pull-right">
                <a href="${_base}/usr/toLogin" class="btn btn-default">Sign in</a>
                <button type="button" class="btn btn-success">Sign up</button>
            </p>
        </#if>
    </div>
</nav>
</#macro>

<#macro searchBox>
<div class="row">
    <div class="col-lg-3">
        <!-- placeholder -->
    </div>
    <div class="col-lg-6">
        <div class="input-group">
            <input type="text" id="searchKey" name="searchKey" class="form-control" placeholder="Search for...">
                <span class="input-group-btn">
                    <button id="searchBtn" name="searchBtn" class="btn btn-success" type="button">Search</button>
                </span>
        </div>
    </div>
</div>
</#macro>

