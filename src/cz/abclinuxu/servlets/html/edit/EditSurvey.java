/*
 *  Copyright (C) 2005 Leos Literak
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public
 *  License as published by the Free Software Foundation; either
 *  version 2 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; see the file COPYING.  If not, write to
 *  the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 *  Boston, MA 02111-1307, USA.
 */
package cz.abclinuxu.servlets.html.edit;

import cz.abclinuxu.AbcException;
import cz.abclinuxu.servlets.Constants;
import cz.abclinuxu.servlets.utils.template.FMTemplateSelector;
import cz.abclinuxu.servlets.utils.ServletUtils;
import cz.abclinuxu.servlets.utils.url.UrlUtils;
import cz.abclinuxu.persistence.Persistence;
import cz.abclinuxu.persistence.PersistenceFactory;
import cz.abclinuxu.persistence.SQLTool;
import cz.abclinuxu.data.User;
import cz.abclinuxu.data.Item;
import cz.abclinuxu.data.XMLHandler;
import cz.abclinuxu.security.ActionCheck;
import cz.abclinuxu.utils.InstanceUtils;
import cz.abclinuxu.utils.Misc;
import cz.abclinuxu.security.Roles;
import cz.abclinuxu.security.ActionProtector;

import cz.abclinuxu.servlets.AbcAutoAction;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.List;
import java.util.Iterator;

import org.dom4j.*;

/**
 * Serves for manipulating of surveys.
 */
public class EditSurvey extends AbcAutoAction {
    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(EditSurvey.class);

    public static final String PARAM_SURVEY = "surveyId";
    public static final String PARAM_TITLE = "title";
    public static final String PARAM_DEFINITION = "definition";
    public static final String PARAM_CHOICES = "choices";

    public static final String VAR_SURVEYS = "SURVEYS";

    private Item getSurvey() throws AbcException {
        Persistence persistence = PersistenceFactory.getPersistence();
        Item item = (Item) InstanceUtils.instantiateParam(PARAM_SURVEY, Item.class, params, request);
        if ( item == null )
            throw new AbcException("Chybí parametr surveyId!");

        persistence.synchronize(item);
        
        if ( item.getType() != Item.SURVEY )
            throw new AbcException("Tato položka není anketa!");

        return item;
    }

    /**
     * Lists existing surveys
     */
    @ActionCheck(permittedRoles = {Roles.SURVEY_ADMIN})
    public String actionList() throws Exception {
        SQLTool sqlTool = SQLTool.getInstance();
        List items = sqlTool.findItemsWithType(Item.SURVEY, 0, 10000);
        env.put(VAR_SURVEYS, items);

        return FMTemplateSelector.select("EditSurvey", "list", env, request);
    }

    @ActionCheck(permittedRoles = {Roles.SURVEY_ADMIN})
    public String actionAdd() throws Exception {
        return FMTemplateSelector.select("EditSurvey", "add", env, request);
    }

    /**
     * Creates new survey
     */
    @ActionCheck(permittedRoles = {Roles.SURVEY_ADMIN}, checkPost = true, checkReferer = true)
    public String actionAdd2() throws Exception {
        Item survey = new Item();
        survey.setType(Item.SURVEY);
        survey.setOwner(user.getId());

        Document document = DocumentHelper.createDocument();
        document.addElement("anketa");
        survey.setData(document);

        boolean canContinue = true;
        canContinue &= setDefinition(params, survey, env);
        canContinue &= setTitle(params, survey, env);
        canContinue &= setChoices(params, survey);

        if ( !canContinue )
            return FMTemplateSelector.select("EditSurvey", "add", env, request);

        PersistenceFactory.getPersistence().create(survey);
        ServletUtils.addMessage("Anketa byla úspěšně vytvořena s číslem "+survey.getId(),env,request.getSession());

        UrlUtils urlUtils = (UrlUtils) env.get(Constants.VAR_URL_UTILS);
        urlUtils.redirect(response, "/EditSurvey/"+survey.getId()+"?action=edit");

        return null;
    }

    @ActionCheck(permittedRoles = {Roles.SURVEY_ADMIN})
    public String actionEdit() throws Exception {
        Item survey;
        Document document;

        try {
            survey = getSurvey();
        } catch (AbcException e) {
            return ServletUtils.showErrorPage(e.getMessage(), env, request);
        }

        document = (Document) survey.getData().clone();

        params.put(PARAM_TITLE, survey.getTitle());

        Element choices = (Element) document.selectSingleNode("/anketa/choices");
        if ( choices != null ) {
            StringBuffer sb = new StringBuffer();
            List nodes = choices.elements("choice");
            for ( Iterator iter = nodes.iterator(); iter.hasNext(); ) {
                Node choice = (Node) iter.next();
                sb.append(choice.getText());
                sb.append("\n");
            }
            choices.detach();
            params.put(PARAM_CHOICES,sb.toString());
        }

        String definition = new XMLHandler(document).toString();
        params.put(PARAM_DEFINITION, definition);

        return FMTemplateSelector.select("EditSurvey", "edit", env, request);
    }

    @ActionCheck(permittedRoles = {Roles.SURVEY_ADMIN}, checkPost = true, checkReferer = true)
    public String actionEdit2() throws Exception {
        boolean canContinue = true;
        Item survey;

        try {
            survey = getSurvey();
        } catch (AbcException e) {
            return ServletUtils.showErrorPage(e.getMessage(), env, request);
        }
        
        Map params = (Map) env.get(Constants.VAR_PARAMS);
        canContinue &= setDefinition(params, survey, env);
        canContinue &= setTitle(params, survey, env);
        canContinue &= setChoices(params, survey);

        if ( !canContinue )
            return FMTemplateSelector.select("EditSurvey", "edit", env, request);

        PersistenceFactory.getPersistence().update(survey);
        ServletUtils.addMessage("Změny byly uloženy.", env, request.getSession());

        UrlUtils urlUtils = (UrlUtils) env.get(Constants.VAR_URL_UTILS);
        urlUtils.redirect(response, "/EditSurvey/"+survey.getId()+"?action=edit");

        return null;
    }

    /**
     * Updates title from parameters. Changes are not synchronized with persistence.
     * @param params map holding request's parameters
     * @param survey survey to be updated
     * @return false, if there is a major error.
     */
    private boolean setTitle(Map params, Item survey, Map env) {
        String name = (String) params.get(PARAM_TITLE);
        if ( Misc.empty(name) ) {
            ServletUtils.addError(PARAM_TITLE, "Zadejte jméno ankety!", env, null);
            return false;
        }

        survey.setTitle(name);
        return true;
    }

    /**
     * Updates XML definition from parameters. Changes are not synchronized with persistence.
     * This method shall be called as first one.
     * @param params map holding request's parameters
     * @param survey survey to be updated
     * @param env environment
     * @return false, if there is a major error.
     */
    private boolean setDefinition(Map params, Item survey, Map env) {
        String definition = (String) params.get(PARAM_DEFINITION);
        if ( Misc.empty(definition) ) {
            ServletUtils.addError(PARAM_DEFINITION, "Zadejte XML s definicí ankety!", env, null);
            return false;
        }

        try {
            Document newDoc = DocumentHelper.parseText(definition);
            Element data = (Element) newDoc.selectSingleNode("/anketa");

            if ( data == null ) {
                ServletUtils.addError(PARAM_DEFINITION, "Chybí značka <anketa>!", env, null);
                return false;
            }

            Node title = survey.getData().selectSingleNode("/anketa/title");
            Node choices = survey.getData().selectSingleNode("/anketa/choices");
            if ( title != null ) {
                title.detach();
                data.add(title);
            }
            if ( choices != null ) {
                choices.detach();
                data.add(choices);
            }
            survey.setData(newDoc);
        } catch (DocumentException e) {
            ServletUtils.addError(PARAM_DEFINITION, "Zadejte validní XML!"+e.getMessage(), env, null);
            return false;
        }
        return true;
    }

    /**
     * Updates choices from parameters. Changes are not synchronized with persistence.
     * @param params map holding request's parameters
     * @param survey survey to be updated
     * @return false, if there is a major error.
     */
    private boolean setChoices(Map params, Item survey) {
        Element choices = (Element) survey.getData().selectSingleNode("/anketa/choices");
        if ( choices != null ) {
            choices.detach();
            choices = null;
        }

        String input = (String) params.get(PARAM_CHOICES);
        if ( Misc.empty(input) )
            return true;

        choices = DocumentHelper.makeElement(survey.getData(), "/anketa/choices");
        StringTokenizer stk = new StringTokenizer(input,"\n\r");
        while ( stk.hasMoreTokens() ) {
            String choice = stk.nextToken();
            choices.addElement("choice").setText(choice);
        }

        return true;
    }
}
