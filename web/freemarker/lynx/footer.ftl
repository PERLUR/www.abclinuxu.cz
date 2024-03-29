<!-- obsah konci zde -->

<hr width="100%">

<#if VARS.currentPoll??>
 <#assign relAnketa = VARS.currentPoll, anketa = relAnketa.child, total = anketa.totalVoters,
  url=relAnketa.url?default("/ankety/show/"+relAnketa.id)>
 <#if anketa.multiChoice>
  <#assign type = "CHECKBOX">
  <#else>
  <#assign type = "RADIO">
 </#if>

 <p>
  <b>Anketa</b><br>
  <form action="${URL.noPrefix("/EditPoll/"+relAnketa.id)}" method="POST">
  <i>${anketa.text}</i><br>
  <#list anketa.choices as choice>
   <#assign procento = TOOL.percent(choice.count,total)>
   <input type=${type} name="voteId" value="${choice.id}">
   ${choice.text} (${procento}%) ${TOOL.percentBar(procento)}<br>
  </#list>

  <br>Celkem ${total} hlasů
  <#assign diz=TOOL.findComments(anketa)>
  <a href="${url}">Komentářů: ${diz.responseCount}</a><br>
  <input type="submit" value="Hlasuj">
  </span>
  <input type="hidden" name="url" value="/clanky/show/${relAnketa.id}">
  <input type="hidden" name="action" value="vote">
 </form>
 </p>
</#if>

<h3>Služby</h3>
<p>
<a href="/poradna" class="za_mn_odkaz">Poradna</a>
<a href="/faq" class="za_mn_odkaz">FAQ</a>
<a href="/hardware" class="za_mn_odkaz">Hardware</a>
<a href="/software" class="za_mn_odkaz">Software</a>
<a href="/clanky" class="za_mn_odkaz">Články</a>
<a href="/ucebnice" class="za_mn_odkaz">Učebnice</a>
<a href="/blog" class="za_mn_odkaz">Blogy</a>
<a href="/slovnik" class="za_mn_odkaz">Slovník</a>
<a href="/kdo-je" class="za_mn_odkaz">Osobnosti</a>
<a href="/ankety" class="za_mn_odkaz">Ankety</a>
<a href="/ovladace" class="za_mn_odkaz">Ovladače</a>
<a href="/bazar" class="za_mn_odkaz">Bazar</a>
<a href="http://www.abcprace.cz" class="za_mn_odkaz">Práce</a>
</p>

<h3>O serveru</h3>
<p>
 <a href="${URL.make("/pozadavky")}">Požadavky</a>
 <a href="https://github.com/PERLUR/www.abclinuxu.cz/">GitHub</a>
 <a href="/napoveda/rss-a-jine-pristupy">RSS a PDA</a>
 <a href="/portal/propagace">Propagace</a>
 <a href="/clanky/show/44049">Tým AbcLinuxu</a>
 <a href="/clanky/novinky/pojdte-psat-pro-abclinuxu.cz">Pište pro abclinuxu</a>
 ISSN 1214-1267
</p>

</body>
</html>
