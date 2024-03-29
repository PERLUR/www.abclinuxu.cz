<#macro showHardware(hardware)>
    <h1>${hardware.title!}</h1>

    <table class="hwdetail">
        <#if TOOL.xpath(hardware,"/data/support")??>
            <tr>
                <td><b>Podpora:</b></td>
                <td>
                    <#switch TOOL.xpath(hardware,"data/support")>
                        <#case "complete">kompletní<#break>
                        <#case "partial">částečná<#break>
                        <#case "none">žádná<#break>
                    </#switch>
                </td>
            </tr>
        </#if>

        <#if TOOL.xpath(hardware,"/data/driver")??>
            <tr>
                <td><b>Ovladač:</b></td>
                <td>
                    <#switch TOOL.xpath(hardware,"data/driver")>
                        <#case "kernel">v jádře<#break>
                        <#case "xfree">v X Window serveru<#break>
                        <#case "maker">dodává výrobce<#break>
                        <#case "other">dodává někdo jiný<#break>
                        <#case "none">neexistuje<#break>
                    </#switch>
                </td>
            </tr>
        </#if>

        <#assign hwurl = TOOL.xpath(hardware,"data/driver_url")!"UNDEFINED">
        <#if (hwurl!="UNDEFINED")>
            <tr>
                <td><b>Adresa ovladače:</b></td>
                <td>
                    <a href="${hwurl}" rel="nofollow">${TOOL.limit(hwurl,50,"..")}</a>
                </td>
            <tr>
        </#if>

        <#if TOOL.xpath(hardware,"data/outdated")??>
            <tr>
                <td><b>Zastaralý:</b></td>
                <td>ano</td>
            </tr>
        </#if>
    </table>

    <#if TOOL.xpath(hardware,"data/params")??>
        <h2>Technické parametry</h2>
        <div>
            ${TOOL.render(TOOL.element(hardware.data,"data/params"),USER!)}
        </div>
    </#if>

    <#if TOOL.xpath(hardware,"data/identification")??>
        <h2>Identifikace pod Linuxem</h2>
        <div>
            ${TOOL.render(TOOL.element(hardware.data,"data/identification"),USER!)}
        </div>
    </#if>

    <#if TOOL.xpath(hardware,"data/setup")??>
        <h2>Postup zprovoznění pod Linuxem</h2>
        <div>
            ${TOOL.render(TOOL.element(hardware.data,"data/setup"),USER!)}
        </div>
    </#if>

    <#if TOOL.xpath(hardware,"data/note")??>
        <h2>Poznámka</h2>
        <div>
            ${TOOL.render(TOOL.element(hardware.data,"data/note"),USER!)}
        </div>
    </#if>
</#macro>
