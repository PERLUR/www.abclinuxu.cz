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
package cz.abclinuxu.utils.freemarker;

import cz.abclinuxu.data.*;
import cz.abclinuxu.data.Link;
import cz.abclinuxu.exceptions.PersistenceException;
import cz.abclinuxu.exceptions.InvalidDataException;
import cz.abclinuxu.exceptions.NotFoundException;
import cz.abclinuxu.persistence.PersistenceFactory;
import cz.abclinuxu.persistence.Persistence;
import cz.abclinuxu.persistence.SQLTool;
import cz.abclinuxu.persistence.Nursery;
import cz.abclinuxu.servlets.Constants;
import cz.abclinuxu.servlets.html.edit.EditRating;
import cz.abclinuxu.servlets.utils.url.UrlUtils;
import cz.abclinuxu.data.view.*;
import cz.abclinuxu.utils.config.Configurable;
import cz.abclinuxu.utils.config.ConfigurationException;
import cz.abclinuxu.utils.config.ConfigurationManager;
import cz.abclinuxu.utils.format.*;
import cz.abclinuxu.utils.Misc;
import cz.abclinuxu.utils.SolutionTool;
import cz.abclinuxu.utils.InstanceUtils;
import cz.abclinuxu.utils.Advertisement;
import cz.abclinuxu.utils.TagTool;
import cz.abclinuxu.utils.BeanFetcher;
import cz.abclinuxu.utils.Sorters2;
import cz.abclinuxu.utils.email.monitor.MonitorTool;
import cz.abclinuxu.utils.forms.RichTextEditor;
import cz.abclinuxu.scheduler.EnsureWatchedDiscussionsLimit;
import cz.abclinuxu.scheduler.VariableFetcher;
import cz.abclinuxu.security.ActionProtector;
import cz.abclinuxu.security.Permissions;
import cz.abclinuxu.security.Roles;
import cz.abclinuxu.utils.comparator.SolutionVotesComparator;
import cz.abclinuxu.utils.config.impl.AbcConfig;
import org.dom4j.Document;
import org.dom4j.Node;
import org.dom4j.Element;
import org.dom4j.Branch;
import org.dom4j.DocumentException;
import org.dom4j.io.DOMWriter;
import org.apache.log4j.Logger;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;
import org.apache.regexp.REProgram;
import org.apache.regexp.RECompiler;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.nodes.TagNode;
import org.htmlparser.util.ParserException;
import org.htmlparser.Text;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.regex.Matcher;
import java.util.prefs.Preferences;

import freemarker.template.*;
import freemarker.ext.dom.NodeModel;
import java.util.prefs.BackingStoreException;

/**
 * Various utilities available for templates
 */
public class Tools implements Configurable {
    static Logger log = Logger.getLogger(Tools.class);

    public static final String PREF_REGEXP_REMOVE_TAGS = "RE_REMOVE_TAGS";
    public static final String PREF_REGEXP_VLNKA = "RE_VLNKA";
    public static final String PREF_REPLACEMENT_VLNKA = "REPLACEMENT_VLNKA";
    public static final String PREF_REGEXP_AMPERSAND = "RE_AMPERSAND";
    public static final String PREF_REGEXP_REMOVE_PARAGRAPHS = "RE_REMOVE_PARAGRAPHS";
    public static final String PREF_REGEXP_INLINE_OBJECT = "RE_INLINE_OBJECT";
    public static final String PREF_STORY_RESERVE_PERCENTS = "story.reserve.percents";
    public static final String PREF_NEWS_LETTER_LIMIT_SOFT = "news.letter.limit.soft";
    public static final String PREF_NEWS_LETTER_LIMIT_HARD = "news.letter.limit.hard";
    public static final String PREF_CSS_STYLES = "css.styles";
    public static final String PREF_MISSING_USER_NAME = "missing.user.name";

    static Persistence persistence = PersistenceFactory.getPersistence();
    // TODO replace with java regexp
    static REProgram reRemoveTags, reVlnka, lineBreak, reRemoveParagraphs, reInlineObject, reDetectLink;
    static Pattern reAmpersand;
    static String vlnkaReplacement;
    static int storyReservePercents;
    static int newsLetterSoftLimit, newsLetterHardLimit;
    static Map<String,String> offeredCssStyles;
    private static final User MISSING_USER;

    static {
        MISSING_USER = new User(-1);
        MISSING_USER.setData("<data/>");
        MISSING_USER.setInitialized(true);
        Tools tools = new Tools();
        ConfigurationManager.getConfigurator().configureAndRememberMe(tools);
        Tools.ampersand = Pattern.compile("&");
    }

    private static final Integer MAX_SEEN_COMMENT_ID = Integer.MAX_VALUE;
    public static Pattern ampersand;


    public void configure(Preferences prefs) throws ConfigurationException {
        try {
            String pref = prefs.get(PREF_REGEXP_REMOVE_TAGS, null);
            RECompiler reCompiler = new RECompiler();
            reRemoveTags = reCompiler.compile(pref);
            pref = prefs.get(PREF_REGEXP_VLNKA, null);
            reVlnka = reCompiler.compile(pref);
            vlnkaReplacement = prefs.get(PREF_REPLACEMENT_VLNKA, null);
            lineBreak = reCompiler.compile("[\r\n$]+");
            pref = prefs.get(PREF_REGEXP_AMPERSAND, null);
            reAmpersand = Pattern.compile(pref);
            pref = prefs.get(PREF_REGEXP_REMOVE_PARAGRAPHS, null);
            reRemoveParagraphs = reCompiler.compile(pref);
            pref = prefs.get(PREF_REGEXP_INLINE_OBJECT, null);
            reInlineObject = reCompiler.compile(pref); 
            storyReservePercents = prefs.getInt(PREF_STORY_RESERVE_PERCENTS, 50);
            newsLetterSoftLimit = prefs.getInt(PREF_NEWS_LETTER_LIMIT_SOFT, 400);
            newsLetterHardLimit = prefs.getInt(PREF_NEWS_LETTER_LIMIT_HARD, 500);
            MISSING_USER.setName(prefs.get(PREF_MISSING_USER_NAME, "Nobody"));

            try {
                Preferences subprefs = prefs.node(PREF_CSS_STYLES);
                String[] keys = subprefs.keys();
                offeredCssStyles = new HashMap<String, String>(keys.length);

                for (int i = 0; i < keys.length; i++)
                    offeredCssStyles.put(keys[i], subprefs.get(keys[i], null));
            } catch (BackingStoreException e) {
                throw new ConfigurationException(e.getMessage(), e.getCause());
            }

            String hostname = AbcConfig.getHostname().replaceAll("\\.", "\\\\.");
            String regexp = "<a href=\"https?://" + hostname + "([^\"]+)\"";
            reDetectLink = reCompiler.compile(regexp);

        } catch (RESyntaxException e) {
            log.error("Chyba pri kompilaci regexpu!", e);
        } catch (PatternSyntaxException e) {
            log.error("Chyba pri kompilaci regexpu!", e);
        }
    }

    /**
     * Returns text value of node selected by xpath expression for GenericObject.
     * @throws cz.abclinuxu.exceptions.PersistenceException if object cannot be synchronized
     */
    public static String xpath(GenericObject obj, String xpath) {
        if ( obj==null || !(obj instanceof XMLContainer) )
            return null;
        if ( !obj.isInitialized() )
            persistence.synchronize(obj);
        Document doc = ((XMLContainer)obj).getData();
        if ( doc==null )
            return null;
        Node node = doc.selectSingleNode(xpath);
        return (node!=null)? node.getText() : null;
    }

    /**
     * Extracts value of given xpath expression evaluated on element.
     * @param element XML element
     * @param xpath xpath expression
     * @return Strings
     */
    public static String xpath(Node element, String xpath) {
        Node node = element.selectSingleNode(xpath);
        return (node==null)? null : node.getText();
    }

    /**
     * Extracts element found by xpath from element.
     * @param element XML element
     * @param xpath xpath expression
     * @return extracted Element
     */
    public static Element element(Node element, String xpath) {
        return (Element) element.selectSingleNode(xpath);
    }

    /**
     * Extracts value of given xpath expression evaluated on element.
     * @param element XML element
     * @param xpath xpath expression
     * @return object, that is result of xpath expression, or null
     */
    public static Object xpathValue(Node element, String xpath) {
        if ( element==null )
            return null;
        Object result = element.selectObject(xpath);
        if ( result==null )
            return null;
        if ( result instanceof Node )
            return ((Node) result).getText();
        return result.toString();
    }

    /**
     * Extracts values of given xpath expression evaluated on element.
     * @param element XML element
     * @param xpath xpath expression
     * @return list of Strings
     */
    public static List xpaths(Node element, String xpath) {
        List nodes = element.selectNodes(xpath);
        List result = new ArrayList(nodes.size());
        for (Iterator iter = nodes.iterator(); iter.hasNext();) {
            Node node = (Node) iter.next();
            String value = node.getText();
            if ( !Misc.empty(value) )
                result.add(value);
        }
        return result;
    }

    /**
     * @param relationId object holding integer value of relation id
     * @return name of child
     */
    public static String childName(Number relationId) {
        Relation relation = (Relation) PersistenceFactory.getPersistence().findById(new Relation(relationId.intValue()));
        return childName(relation);
    }

    /**
     * @param relationId object holding integer value of relation id
     * @return name of child
     */
    public static String childName(String relationId) {
        int id = Integer.parseInt(relationId);
        Relation relation = (Relation) PersistenceFactory.getPersistence().findById(new Relation(id));
        return childName(relation);
    }

    /**
     * @param relation initialized relation
     * @return name of child in this relation.
     */
    public static String childName(Relation relation) {
        if ( relation == null || relation.getChild() == null )
            return null;

        Document data = relation.getData();
        if ( data != null ) {
            Node node = data.selectSingleNode("data/name");
            if ( node != null )
                return node.getText();
        }

        return childName(relation.getChild());
    }

    /**
     * @param obj initialized generic object
     * @return name of child in this relation.
     */
    public static String childName(GenericObject obj) {
        if ( ! obj.isInitialized() )
            persistence.synchronize(obj);

        if ( obj instanceof GenericDataObject ) {
            GenericDataObject gdo = (GenericDataObject) obj;
            String title = gdo.getTitle();
            if (title != null)
                return title;
        }

        if ((obj instanceof Item)) {
            int type = ((Item) obj).getType();
            if (type == Item.AUTHOR || type == Item.PERSONALITY)
                return getPersonName((Item) obj);
            if (type == Item.DISCUSSION) {
                log.warn("Discussion without title: " + obj.getId());
                return "Diskuse";
            }
            if (type == Item.NEWS) {
                log.warn("News without title: " + obj.getId());
                return "Zprávička";
            }
        }

        if ( obj instanceof Link )
            return ((Link)obj).getText();

        if ( obj instanceof Poll )
            return removeTags(((Poll)obj).getText());

        if ( obj instanceof User )
            return ((User)obj).getName();

        if ( obj instanceof Server )
            return ((Server)obj).getName();

        String name = obj.getClass().getName();
        name = name.substring(name.lastIndexOf('.')+1);
        name = name.concat(" "+obj.getId());
        return name;
    }

    /**
     * Extracts person name from XML (first name and surname). It can be used only for Items of type
     * Author and Personality.
     * @param item initialized item
     * @return name fetched from XML
     */
    public static String getPersonName(Item item) {
    
    	if (item.getType()==Item.AUTHOR) {
    		StringBuilder sb = new StringBuilder();
    		if(item.getString1()!=null)
                sb.append(item.getString1()).append(" ");
    		if(item.getString2()!=null)
                sb.append(item.getString2());
    		return sb.toString();
    	}
    
        Element root = item.getData().getRootElement();
        StringBuffer sb = new StringBuffer();
        String name = root.elementTextTrim("firstname");
        if (name != null)
            sb.append(name).append(' ');
        sb.append(root.elementTextTrim("surname"));
        return sb.toString();
    }

    /**
     * Concatenates name and surname of the author.
     * @param author an author
     * @return name and surname
     */
    public static String getPersonName(Author author) {
        StringBuilder sb = new StringBuilder();
        if (author.getName() != null)
            sb.append(author.getName()).append(" ");
        if (author.getSurname() != null)
            sb.append(author.getSurname());
        return sb.toString();
    }

    /**
     * Retrieves icon for child of this relation. If relation declares icon,
     * it will be used. Otherwise relation's child's icon will be returned.
     * If there is no icon at all, null will be returned.
     */
    public String childIcon(Relation relation) {
        if ( relation==null || relation.getChild()==null ) return null;
        String name = xpath(relation,"data/icon");
        if ( ! Misc.empty(name) ) return name;
        return xpath(relation.getChild(),"data/icon");
    }

    /**
     * Returns URL for selected relation.
     * @param relation initialized relation
     * @param urlUtils initialized UrlUtils instance
     * @return URL for this relation
     */
    public static String childUrl(Relation relation, UrlUtils urlUtils) {
        if (relation.getUrl() != null)
            return urlUtils.noPrefix(relation.getUrl());

        if (relation.getId() == Constants.REL_BLOGS)
            return urlUtils.noPrefix("/blog");

        if (relation.getId() == Constants.REL_NEWS)
            return urlUtils.noPrefix("/zpravicky");

        GenericObject child = relation.getChild();
        if (child instanceof Category) {
            Category category = (Category) child;
            if (category.getType() == Category.FORUM)
                return urlUtils.make("/forum/dir/" + relation.getId());

            if (category.getType() == Category.BLOG)
                return urlUtils.noPrefix("/blog/" + category.getSubType());

            return urlUtils.make("/dir/" + relation.getId());
        }

        if (child instanceof Item) {
            Item item = (Item) child;
            if (item.getType() == Item.BLOG || item.getType() == Item.UNPUBLISHED_BLOG) {
                Category blog = (Category) relation.getParent();
                sync(blog);
                return urlUtils.noPrefix(getUrlForBlogStory(blog.getSubType(), item.getCreated(), relation.getId()));
            } else if (item.getType() == Item.DISCUSSION)
                return urlUtils.noPrefix(getUrlForDiscussion(relation));
            else
                return urlUtils.make("/show/" + relation.getId());
        }

        return urlUtils.make("/show/" + relation.getId());
    }

    public String getType(GenericObject obj) {
        if (obj instanceof Item) {
            switch (((Item)obj).getType()) {
                case Item.ADVERTISEMENT:
                    return "reklamní pozice";
                case Item.ARTICLE:
                    return "článek";
                case Item.AUTHOR:
                    return "autor";
                case Item.BAZAAR:
                    return "inzerát";
                case Item.BLOG:
                case Item.UNPUBLISHED_BLOG:
                    return "zápisek";
                case Item.CONTENT:
                    return "wiki obsah";
                case Item.DESKTOP:
                    return "desktop";
                case Item.DICTIONARY:
                    return "pojem";
                case Item.DISCUSSION:
                    return "diskuse";
                case Item.DRIVER:
                    return "ovladač";
                case Item.EVENT:
                case Item.UNPUBLISHED_EVENT:
                    return "událost";
                case Item.FAQ:
                    return "FAQ";
                case Item.GROUP:
                    return "systémová skupina";
                case Item.HARDWARE:
                    return "hardwarová položka";
                case Item.NEWS:
                    return "zprávička";
                case Item.PERSONALITY:
                    return "osobnost";
                case Item.REQUEST:
                    return "požadavek";
                case Item.ROYALTIES:
                    return "honorář";
                case Item.SERIES:
                    return "seriál";
                case Item.SOFTWARE:
                    return "softwarová položka";
                case Item.SURVEY:
                    return "velká anketa";
                case Item.TOC:
                    return "obsah";
                case Item.TRIVIA:
                    return "trivia";
                case Item.VIDEO:
                    return "video";
                default:
                    return "neznámá položka";
            }
        } else if (obj instanceof Category) {
            return "sekce";
        } else if (obj instanceof Poll) {
            return "anketa";
        } else if (obj instanceof Relation) {
            return "relace";
        } else if (obj instanceof Record) {
            return "záznam";
        } else {
            return obj.getClass().getName();
        }
    }

    /**
     * Finds related documents for given Item.
     * @param item
     * @return list with RelatedDocument instances, if there are any.
     */
    public List getRelatedDocuments(Item item) {
        if (! item.isInitialized())
            item = (Item) persistence.findById(item);

        List elements = item.getData().selectNodes("/data/related/document");
        if (elements.size() == 0)
            return Collections.EMPTY_LIST;

        List related = new ArrayList(elements.size());
        for (Iterator iter = elements.iterator(); iter.hasNext();) {
            Element element = (Element) iter.next();
            RelatedDocument document = new RelatedDocument(element.elementText("url"), element.attributeValue("type"));
            document.setTitle(element.elementText("title"));
            document.setDescription(element.elementText("description"));
            related.add(document);
        }
        return related;
    }

    /**
     * Loads tags assigned to child document of specified relation.
     * @param relation existing relation, the child must be GenericDataObject
     * @return List of tags (empty if no tags have been assigned)
     */
    public static List<Tag> getAssignedTags(Relation relation) {
        return TagTool.getAssignedTags((GenericDataObject) relation.getChild());
    }

    /**
     * Loads revision information for specified object.
     * @param obj object that is wiki
     * @return view object encapsulating information from /data/versioning/revisions element
     */
    public static RevisionInfo getRevisionInfo(GenericDataObject obj) {
        if (! Misc.isWiki(obj))
            throw new InvalidDataException("Objekt " + obj.getId() + " typu " + obj.getType() + " neni wiki!");
        Element elementRevisions = (Element) obj.getData().selectSingleNode("/data/versioning/revisions");
        if (elementRevisions == null)
            throw new InvalidDataException("Objekt " + obj.getId() + " neobsahuje versioning element!");

        String s = elementRevisions.attributeValue("last");
        int revision = Misc.parseInt(s, -1);
        Element elementCommitters = elementRevisions.element("committers");
        s = elementCommitters.elementText("creator");
        User creator = new User(Misc.parseInt(s, -1));
        User last = null;
        s = elementCommitters.elementText("last");
        if (s != null) {
            int id = Misc.parseInt(s, -2);
            if (id == creator.getId())
                last = creator;
            else
                last = new User(id);
        }

        List<User> committers = new ArrayList<User>(3);
        for (Iterator iter = elementCommitters.elements("committer").iterator(); iter.hasNext();) {
            Element element = (Element) iter.next();
            s = element.getText();
            int id = Misc.parseInt(s, -3);
            User user;
            if (id == creator.getId())
                user = creator;
            else if (id == last.getId())
                user = last;
            else
                user = new User(id);
            committers.add(user);
        }

        List syncList = new ArrayList();
        syncList.add(creator);
        if (last != null)
            syncList.add(last);
        syncList.addAll(committers);
        persistence.synchronizeList(syncList);

        RevisionInfo info = new RevisionInfo(revision);
        info.setCreator(creator);
        info.setLastCommiter(last);
        info.setCommitters(committers);
        return info;
    }

    /**
     * Generates list of Links to parents of this object.
     * @param parents list of Relations or cz.abclinuxu.data.view.Link
     * @param o it shall be User or undefined value
     * @return List of Links
     * todo rename to getNavigationPath(), similarly ShowObject.VAR_PARENTS
     */
    public List getParents(List parents, Object o, UrlUtils urlUtils) {
        if (parents.size() == 0)
            return Collections.EMPTY_LIST;

        List result = new ArrayList();
        Relation relation = null;
        cz.abclinuxu.data.view.Link link;
        for ( Iterator iter = parents.iterator(); iter.hasNext(); ) {
            Object next = iter.next();
            if (next instanceof Relation) {
                relation = (Relation) next;
                GenericObject child = relation.getChild();
                if ( child instanceof Category && (child.getId()==Constants.CAT_ROOT || child.getId()==Constants.CAT_SYSTEM ))
                    continue;

                link = new cz.abclinuxu.data.view.Link(childName(relation), childUrl(relation, urlUtils), null);
            } else if (next instanceof cz.abclinuxu.data.view.Link)
                link = (cz.abclinuxu.data.view.Link) next;
            else
                throw new InvalidDataException("Parents shall contain instances of Relation or Link only, but received " + next + "!");

            result.add(link);
        }
        return result;
    }

    /**
     * Tests given object if it is a subportal root category.
     * @param obj object, it can be uninitialized
     * @return true if obj is a root category for some portal
     */
    public static boolean isSubportal(GenericObject obj) {
        if (obj instanceof Category) {
            Category category = (Category) sync(obj);
            return (category.getType() == Category.SUBPORTAL);
        }

        return false;
    }

    /**
     * Checks whether one of the parents is a subportal
     * @param parents A list of parents
     * @return A relation of a subportal, if any
     */
    public static Relation getParentSubportal(List<Relation> parents) {
        for (Relation parent : parents) {
            if (isSubportal(parent.getChild()))
                return parent;
        }

        return null;
    }

    public static Relation getParentSubportal(Relation relation) {
        List parents = persistence.findParents(relation);
        return getParentSubportal(parents);
    }

    /**
     * Gets the relation's parents and tries to find a section, to which the
     * relation belongs in a subportal
     * @param relation
     * @return Null, if the relation doesn't belong to a subportal
     */
    public static Relation getParentSubportalSection(Relation relation) {
        List<Relation> parents = persistence.findParents(relation);
        Relation last = null;

        for (Relation parent : parents) {
            if (isSubportal(parent.getChild()))
                return last;

            last = parent;
        }

        return null;
    }

    /**
     * Calculates percentage with correct rounding. 0<=count<=base.
     * @return Number between 0 and 100
     */
    public int percent(int count, int base) {
        if ( base<=0 ) return 0;
        double percent = 100*count/(double)base;
        return (int)(percent+0.5);
    }

    /**
     * If number of votes for yes is higher than for no, than
     * this question is considered to be solved.
     * @return whether the question is solved
     */
    public static boolean isQuestionSolved(Item item) {
        if (item.getNumeric1() == null)
            return false;
        return item.getNumeric1() > 0;
    }

    /**
     * Finds out, whether the guidepost shall be displayed or not.
     * @deprecated todo remove
     */
    public boolean isGuidePostEnabled(Object user) {
        if (!(user instanceof User))
            return true;
        User userA = (User) user;
        if (!userA.isInitialized())
            sync(userA);
        Element element = (Element) userA.getData().selectSingleNode("/data/settings/guidepost");
        if (element == null)
            return true;
        return "yes".equals(element.getText());
    }

    /**
     * Finds user's signature and display it, unless visitor forbids it.
     * If user or visitor is registered, these objects will be instance of User
     * class. If signature is for any reason not available, null will be returned.
     * @param user user, whose signature shall be displayed.
     * @param visitor visitor, that is viewing this comment.
     * @return user's signature or null.
     */
    public String getUserSignature(Object user, Object visitor) {
        if (user==null || !(user instanceof User))
            return null;
        User userA = (User) user;
        if (!userA.isInitialized())
            sync(userA);
        Element element = (Element) userA.getData().selectSingleNode("/data/personal/signature");
        if (element==null)
            return null;
        if (visitor != null && (visitor instanceof User)) {
            User userVisitor = (User) visitor;
            if (!userVisitor.isInitialized())
                sync(userVisitor);
            Element element2 = (Element) userVisitor.getData().selectSingleNode("//settings/signatures");
            if (element2!=null && !element2.getTextTrim().equalsIgnoreCase("yes"))
                return null;
        }
        return element.getText();
    }

    /**
     * Finds user setting about displaying social bookmarks icons. Default is true.
     * @param visitor maybe user
     * @return true, if social bookmarks icons shall be displayed
     */
    public boolean displaySocialBookmarks(Object visitor) {
        if (visitor == null || !(visitor instanceof User))
            return true;
        return Misc.getNodeSetting(((User)visitor).getData(), "/data/settings/social_bookmarks", true);
    }

    /**
     * Finds user's avatar and displays it, unless visitor forbids it.
     * If user or visitor is registered, these objects will be instance of User
     * class. If avatar is for any reason not available, null will be returned.
     * @param user user, whose avatar shall be displayed. It must be initialized.
     * @param visitor user, that is viewing this comment. It must be initialized, if passed.
     * @return user's avatar url or null.
     */
    public String getUserAvatar(Object user, Object visitor) {
        if (user==null || !(user instanceof User))
            return null;
        User userA = (User) user;
        Element element = (Element) userA.getData().selectSingleNode("/data/profile/avatar");
        if (element == null)
            return null;
        if (visitor != null && (visitor instanceof User)) {
            User userVisitor = (User) visitor;
            Element element2 = (Element) userVisitor.getData().selectSingleNode("//settings/avatars");
            if (element2 != null && ! element2.getTextTrim().equalsIgnoreCase("yes"))
                return null;
        }
        return element.getText();
    }

    /**
     * Creates anchor for the blog of specified user.
     * @param user existing user
     * @param defaultTitle if blog doesn't have set its name, this will be used as anchor text
     * @return anchor to the user's blog
     */
    public String getUserBlogAnchor(User user, String defaultTitle) {
        if (!user.isInitialized())
            sync(user);
        Element element = (Element) user.getData().selectSingleNode("//settings/blog");
        if (element == null)
            return null;

        Category blog = createCategory(element.getTextTrim());
        String title = blog.getTitle();
        if (title == null)
            title = defaultTitle;
        String anchor = "<a href=\"/blog/"+blog.getSubType()+"\">"+title+"</a>";
        return anchor;
    }

    /**
     * Creates list of users, that selected user blocks.
     * @param user user whose blacklist shall be fetched
     * @param onlyUsers true if only registered users (not anonymous visitors without account) shall be returned
     * @return set of integers containing user id and strings containing names of anonymous visitors
     */
    public static Set getBlacklist(User user, boolean onlyUsers) {
        Element elementBlacklist = (Element) user.getData().selectSingleNode("/data/settings/blacklist");
        if (elementBlacklist == null)
            return Collections.emptySet();

        Set blacklist = new HashSet(elementBlacklist.elements().size());
        for ( Iterator iter = elementBlacklist.elements().iterator(); iter.hasNext(); ) {
            Element element = (Element) iter.next();
            if (element.getName().equals("uid")) {
                blacklist.add(new Integer(element.getText()));
                continue;
            }
            if (! onlyUsers)
                blacklist.add(element.getText());
        }
        return blacklist;
    }

    /**
     * Filters blog stories banned by blog administrator or written
     * by a banned user, according to user's preferences.
     * @param stories initialized Item relations
     * @param aUser typically User instance
     * @param count desired amount of returned relations
     * @param filterBanned remove stories banned by an administrator
     * @return relations not banned for any reason
     */

    public static List<Relation> filterBannedStories(List<Relation> stories, Object aUser, int count, boolean filterBanned) {
        Set blockedUsers = Collections.EMPTY_SET;
        List<Relation> result = new ArrayList<Relation>(count);

        if (aUser != null && (aUser instanceof User)) {
            User user = (User) aUser;
            blockedUsers = getBlacklist(user, true);

            if (filterBanned)
                filterBanned = Misc.getNodeSetting(user.getData(), "/data/settings/hp_all_stories", true);
        }

        if (! blockedUsers.isEmpty() || filterBanned) {
            for (Iterator iter = stories.iterator(); iter.hasNext() && result.size() < count;) {
                Relation relation = (Relation) iter.next();
                Item story = (Item) relation.getChild();
                boolean removeStory = blockedUsers.contains(new Integer(story.getOwner()));
                removeStory |= (filterBanned && story.getSingleProperty(Constants.PROPERTY_BANNED_BLOG) != null);

                if (!removeStory)
                    result.add(relation);
            }
        }
        else {
            // don't filter anything, just limit the story count
            if (stories.size() > count)
                result.addAll(stories.subList(0, count));
            else
                result.addAll(stories);
        }

        return result;
    }

    /**
     * Returns the relation count including the reserve.
     * Needed for correct further blog stories filtering.
     * @param count target relation count
     * @return "preloaded" amount
     */
    public static int getPreloadedStoryCount(int count) {
        return count + count*storyReservePercents/100;
    }

    /**
     * @return String containing one asterisk for each ten percents.
     */
    public String percentBar(int percent) {
        if ( percent<5 )       return "";
        else if ( percent<15 ) return "*";
        else if ( percent<25 ) return "**";
        else if ( percent<35 ) return "***";
        else if ( percent<45 ) return "****";
        else if ( percent<55 ) return "*****";
        else if ( percent<65 ) return "******";
        else if ( percent<75 ) return "*******";
        else if ( percent<85 ) return "********";
        else if ( percent<95 ) return "*********";
        else                   return "**********";
    }

    /**
     * New operator for servers. It parses list of ids
     * and returns list of servers having same id.
     * They are initialized already.
     */
    public List createServers(SimpleSequence seq) throws TemplateModelException {
        int size = seq.size();
        List list = new ArrayList();
        for (int i=0; i<size; i++) {
            TemplateNumberModel a = (TemplateNumberModel) seq.get(i);
            int id = a.getAsNumber().intValue();
            Server server = (Server) persistence.findById(new Server(id));
            list.add(server);
        }
        return list;
    }

    /**
     * Finds all links for given servers identified by ids. Links are sorted by date in descending order.
     * @param seq list of integers
     * @return map with Servers and their Links
     * @throws TemplateModelException
     * todo use in VariableFetcher instead of UpdateLinks.getFeeds() (without freemarker dependancy)
     */
    public Map<Server, List<Link>> getFeeds(SimpleSequence seq) throws TemplateModelException {
        int size = seq.size();
        Map<Server, List<Link>> feeds = new HashMap(size);
        List<Link> allLinks = new ArrayList<Link>(size * 5);
        for (int i = 0; i < size; i++) {
            TemplateNumberModel a = (TemplateNumberModel) seq.get(i);
            int id = a.getAsNumber().intValue();
            Server server = (Server) persistence.findById(new Server(id));
            List children = server.getChildren();
            List links = new ArrayList(children.size());

            for (Iterator iter2 = children.iterator(); iter2.hasNext();) {
                Relation relation = (Relation) iter2.next();
                Link link = (Link) relation.getChild();
                links.add(link);
                allLinks.add(link);
            }
            feeds.put(server, links);
        }

        Tools.syncList(allLinks);
        for (List links : feeds.values()) {
            Sorters2.byDate(links, Sorters2.DESCENDING);
        }
        return feeds;
    }

    /**
     * If <code>str</code> is longer than <code>max</code>, it
     * is shortened to length of <code>max-suffix.length()</code>
     * and <code>suffix</code> is appended.
     */
    public static String limit(String str, int max, String suffix) {
        if ( str==null || str.length()==0 ) return "";
        if ( str.length()<=max ) return str;

        if ( suffix==null ) suffix = "";
        StringBuffer sb = new StringBuffer(str);
        int suffixLength = suffix.length();
        if ( suffixLength>0 ) {
            sb.insert(max-suffixLength,suffix);
        }
        sb.setLength(max);
        return sb.toString();
    }

    /**
     * If <code>str</code> is longer than <code>max</code>, it
     * is shortened to length of <code>max-prefix.length()</code>
     * and <code>prefix</code> is added to start.
     */
    public static String reverseLimit(String str, int max, String prefix) {
        if (str == null || str.length() == 0) return "";
        if (str.length() <= max) return str;

        if (prefix == null) prefix = "";
        StringBuffer sb = new StringBuffer(max);
        sb.append(prefix);
        int index = str.length() + prefix.length() - max;
        sb.append(str.substring(index));
        return sb.toString();
    }

    /**
     * If <code>str</code> is longer than <code>max</code> words, it
     * is shortened to contain only first max words and <code>suffix</code>.
     */
    public static String limitWords(String str, int max, String suffix) {
        if ( str==null || str.length()==0 ) return "";
        StringTokenizer stk = new StringTokenizer(str, " \t\n\r\f", true);
        if (stk.countTokens()<=max)
            return str;

        if ( suffix==null ) suffix = "";
        int i = 0;
        max = 2*max;
        StringBuffer sb = new StringBuffer();
        while (stk.hasMoreTokens() && i<max) {
            sb.append(stk.nextToken());
            i++;
        }
        sb.append(suffix);
        return sb.toString();
    }

    /**
     * Returns string containing count times what.
     * @param what
     * @param count
     * @return multiple concatenation of argument what.
     */
    public static String repeatString(String what, int count) {
        if (count<=0)
            return "";
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<count; i++)
            sb.append(what);
        return sb.toString();
    }

    /**
     * Appends second parameter to the first one, separating them by comma. If the first parameter is empty,
     * only the second parameter is returned.
     * @param string string after which second parameter shall be appended
     * @param appended string to be appended
     * @return either second parameter or second parameter appended behind first parameter and comma
     */
    public static String append(String string, String appended) {
        if (string == null || string.length() == 0)
            return appended;
        else
            return string + ", " + appended;
    }

    /**
     * Extracts selected part of given list. Implementation
     * is aware of list's limits, so IndexOutOfBounds is never
     * thrown.
     */
    public static <T> List<T> sublist(List<T> list, int start, int count) {
        if (list == null)
            return null;
        if (start >= list.size())
            return null;

        int end = start + count;
        if (end >= list.size())
            end = list.size();
        return list.subList(start, end);
    }

    /**
     * Extracts selected part of given set. Implementation
     * is aware of list's limits, so IndexOutOfBounds is never
     * thrown.
     */
    public static <T> List<T> sublist(Collection<T> list, int start, int count) {
        if (list == null)
            return null;
        if (start >= list.size())
            return null;

        return sublist(new LinkedList(list), start, count);
    }

    /**
     * Creates list in order split to columns. For example if original list contained
     * elements 1,2,3,4,5,6 and number of columns is 2, output will hold 1,4,2,5,3,6.
     * You can the sequentially iterate the list and easily create two columns (1,2,3)
     * and (4,5,6).
     * @param list original list
     * @param columns number of columns
     * @return new list ordered by columns
     */
    public List columnize(List list, int columns) {
        int size = list.size();
        int rows = size/columns;
        if (size%columns!=0)
            rows ++;

        List result = new ArrayList(size);
        for (int i=0; i<rows; i++) {
            for (int j = 0; j<columns; j++) {
                int index = i + j * rows;
                if (index<size)
                    result.add(list.get(index));
            }
        }

        return result;
    }

    /**
     * Extracts new string from str. Workaround for freemarker,
     * which uses BigDecimal as integer holder, so String.substring(int)
     * is not recognized by beans introspection.
     */
    public String substring(String str, java.math.BigDecimal from) {
        return str.substring(from.intValue());
    }

    /**
     * Synchronizes object with persistence if it is not initialized.
     * For relation, its child is synchronized too.
     */
    public static GenericObject sync(GenericObject obj) {
        if ( ! obj.isInitialized() )
            persistence.synchronize(obj);
        if ( obj instanceof Relation ) {
            GenericObject child = ((Relation)obj).getChild();
            if ( ! child.isInitialized() )
                persistence.synchronize(child);
        }
        return obj;
    }

    /**
     * Synchronizes list of GenericObjects. The collection shall not contain duplicates,
     * they will not be initialized.
     */
    public static List syncList(Collection collection) throws PersistenceException {
        if (collection.size() == 0)
            return Collections.EMPTY_LIST;

        List list;
        if (! (collection instanceof List) )
            list = new ArrayList(collection);
        else
            list = (List) collection;

        persistence.synchronizeList(list);
        if (list.get(0) instanceof Relation) {
            List children = new ArrayList(list.size());
            for (Iterator iter = list.iterator(); iter.hasNext();) {
                GenericObject obj = (GenericObject) iter.next();
                if (obj instanceof Relation)
                    children.add(((Relation)obj).getChild());
            }
            persistence.synchronizeList(children);
        }
        return list;
    }

    /**
     * Initialize children of relations children.
     * Consequent genericObject.getChildren() runs much faster.
     * @param relations list of initialized relations
     */
    public static void initializeChildren(List<Relation> relations) {
        List children = new ArrayList(relations.size());
        for (Iterator iter = relations.iterator(); iter.hasNext();) {
            Relation relation = (Relation) iter.next();
            children.add(relation.getChild());
        }
        Nursery.getInstance().initChildren(children);
    }

    /**
     * This method instantiates user and synchronizes it.
     * @return synchronized User.
     */
    public static User createUser(String id) {
        int uid = Integer.parseInt(id);
        return createUser(uid);
    }

    /**
     * This method instantiates user and synchronizes it. If the user is not found, virtual user with negative id
     * is returned instead.
     * @return synchronized User.
     */
    public static User createUser(int id) {
        try {
            User user = new User(id);
            return (User) persistence.findById(user);
        } catch (NotFoundException e) {
            return MISSING_USER;
        }
    }

    /**
     * This method instantiates list of users and synchronizes them.
     * @return synchronized Users.
     */
    public static List<User> createUsers(List<Integer> userIds) {
        List<User> users = new ArrayList<User>(userIds.size());
        for (Integer id : userIds) {
            users.add(new User(id));
        }
        syncList(users);
        return users;
    }

    /**
     * This method instantiates category and synchronizes it.
     * @return synchronized category.
     */
    public static Category createCategory(int id) {
        Category category = new Category(id);
        return (Category) persistence.findById(category);
    }

    /**
     * This method instantiates category and synchronizes it.
     * @return synchronized category.
     */
    public static Category createCategory(String id) {
        int i = Integer.parseInt(id);
        Category category = new Category(i);
        return (Category) persistence.findById(category);
    }

    /**
     * This method instantiates relation and synchronizes it.
     * @return synchronized relation
     */
    public static Relation createRelation(int id) {
        return (Relation) sync(new Relation(id));
    }

    /**
     * This method instantiates relation and synchronizes it.
     * @return synchronized relation
     */
    public static Relation createRelation(String id) {
        int i = Integer.parseInt(id);
        return createRelation(i);
    }

    /**
     * Converts list of relation ids to list of initialized relations.
     * @param ids list of relation id
     * @return list of initialized Relations
     */
    public static List<Relation> createRelations(List<Integer> ids) {
        if (ids == null || ids.isEmpty())
            return Collections.emptyList();
        List<Relation> relations = new ArrayList<Relation>();
        for (Integer id : ids) {
            relations.add(new Relation(id));
        }
        syncList(relations);
        return relations;
    }

    /**
     * Wraps relation into Screenshot object.
     * @param relation relation containing Item of type Screenshot.
     * @return view object
     */
    public static Desktop createScreenshot(Relation relation) {
        sync(relation);
        return new Desktop(relation);
    }

    /**
     * Finds tag with given id.
     * @param id id of the tag
     * @return Tag or null, if not found
     */
    public static Tag findTag(String id) {
        return TagTool.getById(id);
    }

    /**
     * Wraps Document into freemarker friendly format.
     * @param doc non-null document
     * @return XML-aware model
     * @throws DocumentException
     */
    public static NodeModel asNode(Document doc) throws DocumentException {
        return NodeModel.wrap((new DOMWriter().write(doc)));
    }

    /**
     * @param type type of counter to be fetched
     * @return counter value for selected GenericObject
     */
    public static int getCounterValue(GenericObject obj, String type) {
        return persistence.getCounterValue(obj, type);
    }

    /**
     * Fetches values of counter for children in list of relations.
     * @param relations initialized list of relations
     * @param type type of counter to be fetched
     * @return map where key is GenericObject and value is Number with its counter.
     */
    public static Map<GenericObject, Integer> getRelationCountersValue(List relations, String type) {
        if (relations == null || relations.isEmpty())
            return Collections.emptyMap();
        List list = new ArrayList(relations.size());
        for (Object relation1 : relations) {
            Relation relation = (Relation) relation1;
            list.add(relation.getChild());
        }
        return persistence.getCountersValue(list, type);
    }

    /**
     * This method groups relations by their children's type. Each type represents
     * one of Constants.TYPE_* strings. The key represents list of relations, where
     * children are same type.
     */
    public static Map<String, List<Relation>> groupByType(List relations) throws PersistenceException {
        return groupByType(relations, null);
    }

    /**
     * This method groups relations by their children's type. Each type represents
     * one of Constants.TYPE_* strings. The key represents list of relations, where
     * all children have same type. The classFilter gives possibility to include
     * only children of certain type. If it is empty or null, all classes are considered,
     * otherwise only specified classes will be included in result. Possible value is
     * class name (not FQCN).
     * @param classFilter comma separated list of classes, that may be included in the list
     */
    public static Map<String, List<Relation>> groupByType(List relations, String classFilter) throws PersistenceException {
        if (relations==null)
            return Collections.emptyMap();
        else
            if (!Misc.empty(classFilter))
                relations = new ArrayList(relations);

        boolean itemYes, recordYes, categoryYes, userYes, pollYes, linkYes, dataYes, serverYes;
        if (Misc.empty(classFilter))
            itemYes = recordYes = categoryYes = userYes = pollYes = linkYes = dataYes = serverYes = true;
        else {
            itemYes = recordYes = categoryYes = userYes = pollYes = linkYes = dataYes = serverYes = false;
            StringTokenizer stk = new StringTokenizer(classFilter, ",; ");
            while (stk.hasMoreTokens()) {
                String className = stk.nextToken();
                if ("Item".equalsIgnoreCase(className))
                    itemYes = true;
                else if ("Record".equalsIgnoreCase(className))
                    recordYes = true;
                else if ("Category".equalsIgnoreCase(className))
                    categoryYes = true;
                else if ("Data".equalsIgnoreCase(className))
                    dataYes = true;
                else if ("User".equalsIgnoreCase(className))
                    userYes = true;
                else if ("Poll".equalsIgnoreCase(className))
                    pollYes = true;
                else if ("Link".equalsIgnoreCase(className))
                    linkYes = true;
                else if ("Server".equalsIgnoreCase(className))
                    serverYes = true;
            }
        }

        boolean needsSync = false;
        GenericObject child;
        for (Iterator iterator = relations.iterator(); iterator.hasNext();) {
            Relation relation = (Relation) iterator.next();
            if (!relation.isInitialized()) {
                needsSync = true;
                continue;
            }

            child = relation.getChild();
            if (!itemYes && child instanceof Item) {
                iterator.remove();
                continue;
            } else if (!recordYes && child instanceof Record) {
                iterator.remove();
                continue;
            } else if (!categoryYes && child instanceof Category) {
                iterator.remove();
                continue;
            } else if (!dataYes && child instanceof Data) {
                iterator.remove();
                continue;
            } else if (!pollYes && child instanceof Poll) {
                iterator.remove();
                continue;
            } else if (!userYes && child instanceof User) {
                iterator.remove();
                continue;
            } else if (!linkYes && child instanceof Link) {
                iterator.remove();
                continue;
            } else if (!serverYes && child instanceof Server) {
                iterator.remove();
                continue;
            }

            if (! child.isInitialized())
                needsSync = true;
        }
        if (needsSync)
            syncList(relations);

        Map<String, List<Relation>> map = new HashMap<String, List<Relation>>();
        for (Iterator iter = relations.iterator(); iter.hasNext();) {
            Relation relation = (Relation) iter.next();

            child = relation.getChild();
            if ( child instanceof Category )
                Misc.storeToMap(map, Constants.TYPE_CATEGORY, relation);
            else if ( child instanceof Item ) {
                Item item = (Item) child;
                switch (item.getType()) {
                    case Item.HARDWARE:
                        Misc.storeToMap(map, Constants.TYPE_MAKE, relation);
                        break;
                    case Item.DISCUSSION:
                        Misc.storeToMap(map, Constants.TYPE_DISCUSSION, relation);
                        break;
                    case Item.ARTICLE:
                        Misc.storeToMap(map, Constants.TYPE_ARTICLE, relation);
                        break;
                    case Item.DRIVER:
                        Misc.storeToMap(map, Constants.TYPE_DRIVER, relation);
                        break;
                    case Item.NEWS:
                        Misc.storeToMap(map, Constants.TYPE_NEWS, relation);
                        break;
                    case Item.REQUEST:
                        Misc.storeToMap(map, Constants.TYPE_REQUEST, relation);
                        break;
                    case Item.ROYALTIES:
                        Misc.storeToMap(map, Constants.TYPE_ROYALTIES, relation);
                        break;
                    case Item.CONTENT:
                        Misc.storeToMap(map, Constants.TYPE_DOCUMENTS, relation);
                        break;
                    case Item.VIDEO:
                        Misc.storeToMap(map, Constants.TYPE_VIDEO, relation);
                        break;
                }
            } else if ( child instanceof Record )
                Misc.storeToMap(map, Constants.TYPE_RECORD, relation);
            else if ( child instanceof Data )
                Misc.storeToMap(map, Constants.TYPE_DATA, relation);
            else if ( child instanceof Link )
                Misc.storeToMap(map, Constants.TYPE_LINK, relation);
            else if ( child instanceof Poll )
                Misc.storeToMap(map, Constants.TYPE_POLL, relation);
            else if (child instanceof User)
                Misc.storeToMap(map, Constants.TYPE_USER, relation);
            else if ( child instanceof Server )
                Misc.storeToMap(map, Constants.TYPE_SERVER, relation);
        }
        return map;
    }

    /**
     * This method performs visualization enhancements.
     * It replaces smilies with appropriate images.
     * @param str   text to be rendered
     * @param o     optional User instance
     */
    public String render(String str, Object o) {
        if ( Misc.empty(str) )
            return "";

        boolean renderEmoticons = allowEmoticons(o);
        return renderText(str, renderEmoticons);
    }

    /**
     * This method renders given Element.
     * @param el   DOM4J element to be rendered
     * @param o    optional User instance
     */
    public String render(Object el, Object o) {
        if (el == null || !(el instanceof Element))
            return "";

        boolean renderEmoticons = allowEmoticons(o);
        Element element = (Element) el;
        String input = element.getText();
        return renderText(input, renderEmoticons);
    }

    /**
     * Renders text.
     * @param text string to be rendered
     * @param emoticons when true, emoticons will be replaced by images
     * @return text
     */
    public static String renderText(String text, boolean emoticons) {
        Map params = new HashMap(1, 1.0f);
        if (emoticons)
            params.put(Renderer.RENDER_EMOTICONS, Boolean.TRUE);
        return HTMLFormatRenderer.getInstance().render(text, params);
    }

    public static boolean detectSimpleFormat(Element element) {
        int f = Misc.parseInt(element.attributeValue("format"), -1);
        switch (f) {
            case -1: {
                String input = element.getText();
                Format format = FormatDetector.detect(input);
                if (format.equals(Format.SIMPLE))
                    return true;
                else
                    return false;
            }
            case 0: return true;
        }
        return false;
    }

    /**
     * Detects whether emoticons shall be rendered as image. Default is true.
     * @param o probably User instance
     * @return true when emoticons shall be rendered as image
     */
    public boolean allowEmoticons(Object o) {
        boolean renderEmoticons = true;
        if (o != null && (o instanceof User)) {
            Node node = ((User)o).getData().selectSingleNode("/data/settings/emoticons");
            if (node != null && "no".equals(node.getText()))
                renderEmoticons = false;
        }
        return renderEmoticons;
    }

    /**
     * Tests, whether specified object <code>obj</code> is instance of selected
     * class <code>clazz</code> and if even if it belongs to certain <code>type</code>
     * (for GenericDataObject subclasses). GenericObject <code>obj</code> shall be
     * initialized.
     * @deprecated
     */
    public boolean is(GenericObject obj, String clazz, String type) {
        if ( "Item".equalsIgnoreCase(clazz) ) {
            if ( ! (obj instanceof Item) ) return false;
            if ( type==null ) return true;
            switch (((Item)obj).getType()) {
                case Item.HARDWARE:
                    return Constants.TYPE_MAKE.equalsIgnoreCase(type); // todo nema zde byt spise TYPE_HARDWARE?
                case Item.SOFTWARE:
                    return Constants.TYPE_SOFTWARE.equalsIgnoreCase(type);
                case Item.ARTICLE:
                    return Constants.TYPE_ARTICLE.equalsIgnoreCase(type);
                case Item.DISCUSSION:
                    return Constants.TYPE_DISCUSSION.equalsIgnoreCase(type);
                case Item.REQUEST:
                    return Constants.TYPE_REQUEST.equalsIgnoreCase(type);
                case Item.DRIVER:
                    return Constants.TYPE_DRIVER.equalsIgnoreCase(type);
                default: return false;
            }
        }
        if ( "Record".equalsIgnoreCase(clazz) ) {
            if ( ! (obj instanceof Record) ) return false;
            if ( type==null ) return true;
            switch (((Record)obj).getType()) {
                case Record.ARTICLE: return "Article".equalsIgnoreCase(type);
                case Record.DISCUSSION: return "Discussion".equalsIgnoreCase(type);
                default: return false;
            }
        }
        if ( "Category".equalsIgnoreCase(clazz) ) {
            return (obj instanceof Category);
        }
        return false;
    }

    /**
     * @return integer value of <code>str</code> or 0
     */
    public int parseInt(String str) {
        if ( ! Misc.empty(str) ) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {}
        }
        return 0;
    }

    /**
     * Encodes all mapping from <code>params</code> except those listed
     * in <<code>prohibited</code> list as form hidden input.
     */
    public static String saveParams(Map params, SimpleSequence prohibited) throws TemplateModelException {
        List exceptions = null;
        if (prohibited.size() > 0)
            exceptions = prohibited.toList();
        return saveParams(params, exceptions);
    }

    /**
     * Encodes all mapping from <code>params</code> except those listed
     * in <<code>prohibited</code> list as form hidden input.
     */
    public static String saveParams(Map params, List exceptions) {
        if (params == null || params.size() == 0)
            return "";
        if (exceptions == null)
            exceptions = Collections.EMPTY_LIST;

        StringBuffer sb = new StringBuffer(params.size() * 50);
        Set entries = params.keySet();
        for (Iterator iter = entries.iterator(); iter.hasNext();) {
            String key = (String) iter.next();
            if (exceptions.contains(key))
                continue;

            List values = null;
            if (params.get(key) instanceof List)
                values = (List) params.get(key);
            else
                values = Collections.singletonList(params.get(key));

            for (Iterator iter2 = values.iterator(); iter2.hasNext();) {
                String value = (String) iter2.next();
                sb.append("<input type=\"hidden\" name=\"");
                sb.append(key);
                sb.append("\" value=\"");
                sb.append(encodeSpecial(value));
                sb.append("\">\n");
            }
        }
        return sb.toString();
    }

    /**
     * Does standard HTML conversions like & to &amp;amp; or &lt; to &amp;lt;. Use ?html
     * in freemarker templates!
     * @return Modified String, which may be inserted into html page without affecting its structure.
     */
    public static String encodeSpecial(String in) {
        if ( in==null || in.length()==0 )
            return "";
        StringBuffer sb = new StringBuffer(in.length());
        for (int i = 0; i < in.length(); i++) {
            int c = in.charAt(i);
            switch (c) {
                case '"': sb.append("&quot;");break;
                case '<': sb.append("&lt;");break;
                case '>': sb.append("&gt;");break;
                default: sb.append((char)c);
            }
        }

        String out = sb.toString();
        Matcher matcher = reAmpersand.matcher(out);
        return matcher.replaceAll("&amp;");
    }

    /**
     * Creates URI parameter with current user's ticket.
     * @param aUser instance of User if user is logged in
     * @param firstParam true if this shall be the first parameter in uri
     * @return ticket uri parameter if user is logged in, empty string otherwise
     */
    public String ticket(Object aUser, boolean firstParam) {
        String ticket = ticketValue(aUser);
        if (ticket == null || ticket.length() == 0)
            return "";

        return ((firstParam) ? "?" : "&amp;") + ActionProtector.PARAM_TICKET + "=" + ticket;
    }

    /**
     * @param aUser instance of User if user is logged in
     * @return value of user's ticket if user is logged in, empty string otherwise
     */
    public String ticketValue(Object aUser) {
        if (aUser == null || ! (aUser instanceof User))
            return "";

        User user = (User) aUser;
        String ticket = user.getSingleProperty(Constants.PROPERTY_TICKET);
        if (ticket == null)
            return "";
        return ticket;
    }

    /**
     * Concatenates all pages of the article.
     * @return complete text of the article
     */
    public static String getCompleteArticleText(Item article) {
        Map children = Tools.groupByType(article.getChildren());
        List records = (List) children.get(Constants.TYPE_RECORD);
        Record record = (Record) ((Relation) records.get(0)).getChild();
        if ( !record.isInitialized() )
            persistence.synchronize(record);

        StringBuffer sb = new StringBuffer(5000);
        List nodes = record.getData().selectNodes("/data/content");
        if ( nodes.size()==0 )
            throw new InvalidDataException("Záznam "+record.getId()+" má špatný obsah!");
        else {
            for ( Iterator iter = nodes.iterator(); iter.hasNext(); )
                sb.append(((Element) iter.next()).getText());
        }
        return sb.toString();
    }

    /**
     * Finds advertisement for given position on specified uri. The user may eventually
     * have paid not to see any advertisements. If the position is not defined, HTML comment
     * with info is returned.
     * @param position identifier of the position
     * @param vars freemarker environment
     * @return advertisement or empty string
     */
    public static String getAdvertisement(String position, freemarker.ext.beans.HashAdapter vars) {
        Map env = new HashMap();
        env.put(Constants.VAR_REQUEST_URI, vars.get(Constants.VAR_REQUEST_URI));
        env.put(Constants.VAR_CSS_URI, vars.get(Constants.VAR_CSS_URI));
        env.put(Constants.VAR_USER, vars.get(Constants.VAR_USER));
        env.put("ASSIGNED_TAGS", vars.get("ASSIGNED_TAGS"));
        return Advertisement.getAdvertisement(position, env);
    }

    /**
     * Returns author from user object
     * @param uid id of user
     * @return Author object if found or {@code null} if there is no author for
     *         given object
     */
    public static Author getAuthor(int uid) {
        SQLTool sqlTool = SQLTool.getInstance();
        Relation relation = sqlTool.findAuthorByUserId(uid);
        if (relation == null)
            return null;

        return BeanFetcher.fetchAuthor(relation, BeanFetcher.FetchType.EAGER);
    }

    /**
     * Finds if given author is also author.
     * @param uid id of user
     * @return true if user has role of author
     */
    public static boolean isAuthor(int uid) {
        SQLTool sqlTool = SQLTool.getInstance();
        Relation relation = sqlTool.findAuthorByUserId(uid);
        return (relation != null);
    }

    /**
     * This method removes all tags from text.
     */
    public static String removeTags(String text) {
        if (text==null || text.length()==0)
            return "";
        if (text.indexOf('<')==-1)
            return text;
        try {
//            return StripTags.process(text);
            return new RE(reRemoveTags, RE.MATCH_MULTILINE).subst(text,"");
        } catch (Throwable e) {
            log.warn("Oops, remove tags regexp failed on '"+text+"'!", e);
            return text;
        }
    }

    /**
     * Converts Item with type Discussion to object Discussion.
     * @param obj Item
     * @param maybeUser this may be instance of User that is viewing this discussion
     * @param rid relation id of this discussion
     * @param saveLast if true and maybeUser is instance of User, then latest comment id will be saved
     */
    public static Discussion createDiscussionTree(GenericObject obj, Object maybeUser, int rid, boolean saveLast) throws PersistenceException {
        if (!obj.isInitialized())
            obj = persistence.findById(obj);
        if ( !InstanceUtils.checkType(obj, Item.class, Item.DISCUSSION) )
            throw new IllegalArgumentException("Not a discussion: "+obj);

        Item item = (Item) obj;
        Document document = item.getData();
        User user = null;
        if (maybeUser instanceof User)
            user = (User) maybeUser;

        Discussion discussion = new Discussion();
        discussion.setId(obj.getId());
        discussion.setRelationId(rid);
        discussion.setFrozen(document.selectSingleNode("/data/frozen") != null);
        discussion.setMonitorSize(item.getMonitorCount());
        if (user != null)
            discussion.setMonitored(isMonitored(item, user));

        if (item.getChildren().isEmpty())
            return discussion;

        Map<String, List<Relation>> childrenMap = groupByType(item.getChildren());
        discussion.setAttachments(childrenMap.get(Constants.TYPE_DATA));

        List recordRelations = childrenMap.get(Constants.TYPE_RECORD);
        if (recordRelations == null)
            return discussion;

        Relation child = (Relation) recordRelations.get(0);
        Record record = (Record) child.getChild();
        record = (Record) persistence.findById(record);

        DiscussionRecord dizRecord = (DiscussionRecord) record.getCustom();
        if (dizRecord.getThreads().isEmpty())
            return discussion;

        List<Solution> solutions = new ArrayList<Solution>(SolutionTool.get(item));
        Map<Solution, Comment> mapSolutions = new LinkedHashMap<Solution, Comment>(solutions.size());

        Collections.sort(solutions, new SolutionVotesComparator());
        Collections.reverse(solutions);

        for (Solution s : solutions) {
            Comment c = dizRecord.getComment(s.getId());
            if (c == null) {
                log.warn("Comment not found for solution: "+s);
                continue;
            }
            c.setVoters(s.getVoters());
            c.setSolution(true);
            mapSolutions.put(s, c);
        }

        discussion.setSolutions(mapSolutions);

        dizRecord.walkComments(new DiscussionRecord.CommentWalker() {
            public boolean process(Comment c) {
                if (c.isSolution() && c.getVoters().isEmpty()) {
                    c.setSolution(false);
                    c.setVoters(null);
                }
                return true;
            }
        });

        discussion.init(dizRecord);

        if (user != null && saveLast) {
            SQLTool sqlTool = SQLTool.getInstance();
            discussion.setBlacklist(getBlacklist(user, false));
            Integer lastSeen = user.getLastSeenComment(obj.getId());
            if (lastSeen != null)
                discussion.setUnreadComments(lastSeen);
            sqlTool.insertLastSeenComment(user.getId(), obj.getId(), discussion.getGreatestId());
            user.storeLastSeenComment(obj.getId(), discussion.getGreatestId());
            EnsureWatchedDiscussionsLimit.checkLimits(user.getId());
        }

        return discussion;
    }

    /**
     * Creates empty discussions (no threads).
     * @return empty discussion
     */
    public Discussion createEmptyDiscussion() {
        return new Discussion();
    }

    /**
     * Creates empty discussions (no threads), but with attachments from supplied Item.
     * @return empty discussion with initialized attachments
     */
    public Discussion createEmptyDiscussionWithAttachments(Item item) {
        Discussion discussion = new Discussion();
        Map<String, List<Relation>> childrenMap = groupByType(item.getChildren(), "Data");
        discussion.setAttachments(childrenMap.get(Constants.TYPE_DATA));
        return discussion;
    }

    /**
     * Analyzes initialized relation containing bog story.
     * @param relation initialized relation
     * @param withContent flag whether to parse perex / content
     * @param withCategory flag whether to parse category of this story
     * @return bean
     */
    public static BlogStory analyzeBlogStory(Relation relation, boolean withContent, boolean withCategory) {
        Item item = (Item) relation.getChild();
        Category blog = (Category) sync(relation.getParent());
        BlogStory story = new BlogStory();
        story.setTitle(item.getTitle());
        story.setRelation(relation);
        story.setUrl(getUrlForBlogStory(relation));
        story.setBlogUrl("/blog/" + blog.getSubType());
        story.setBlogTitle(blog.getTitle());
        story.setAuthor(createUser(item.getOwner()));
        story.setCreated(item.getCreated());
        story.setDigest(! item.getProperty(Constants.PROPERTY_BLOG_DIGEST).isEmpty());

        Map<String, List<Relation>> children = groupByType(item.getChildren(), "Data,Item,Poll");
        List list = children.get(Constants.TYPE_POLL);
        if (list != null)
            story.setPolls(list.size());
        list = children.get(Constants.TYPE_DATA);
        if (list != null && list.size() > 0)
            story.setImages(list.size());
        list = children.get(Constants.TYPE_VIDEO);
        if (list != null)
            story.setVideos(list.size());
        list = children.get(Constants.TYPE_DISCUSSION);
        if (list != null && list.size() > 0)
            story.setDiscussion(analyzeDiscussion((Relation) list.get(0)));

        if (withContent) {
            Element element = (Element) item.getData().selectSingleNode("/data/perex");
            if (element != null)
                story.setPerex(element.getText());

            element = (Element) item.getData().selectSingleNode("/data/content");
            story.setContent(element.getText());
        }

        if (withCategory) {
            String xpath = "//category[@id='" + item.getSubType() + "']";
            Element element = (Element) blog.getData().selectSingleNode(xpath);
            if (element != null) {
                String id = element.attributeValue("id");
                String name = element.attributeValue("name");
                String url = element.attributeValue("url");
                if (!Misc.empty(name)) {
                    BlogCategory category = new BlogCategory(id, name, url);
                    if (!Misc.empty(url))
                        category.setAbsoluteUrl(story.getBlogUrl() + "/" + url);
                    story.setCategory(category);
                }
            }
        }

        return story;
    }

    /**
     * This method is responsible for finding greatest id of comment, that user has read already.
     *
     * @param discussion It may be unitialized
     */
    public static Integer getLastSeenComment(Item discussion, User user) {
        if (user == null)
            return MAX_SEEN_COMMENT_ID;

        List children = discussion.getChildren();
        if (children.size() == 0)
            return MAX_SEEN_COMMENT_ID;

        SQLTool sqlTool = SQLTool.getInstance();
        Integer lastSeen = sqlTool.getLastSeenComment(user.getId(), discussion.getId());
        return (lastSeen == null) ? MAX_SEEN_COMMENT_ID : lastSeen;
    }

    /**
     * @return uninitialized DiscussionHeader.
     */
    public static DiscussionHeader analyzeDiscussion(String s) {
        return new DiscussionHeader(null);
    }

    /**
     * Gathers statistics on given discussion.
     */
    public static DiscussionHeader analyzeDiscussion(Relation relation) {
        if (relation==null)
            return new DiscussionHeader(null);

        if ( ! InstanceUtils.checkType(relation.getChild(), Item.class, Item.DISCUSSION) ) {
            log.error("Relation "+relation.getId()+" doesn't contain item!");
            return null;
        }

        Item item = (Item) relation.getChild();
        DiscussionHeader discussion = new DiscussionHeader(item);
        discussion.relationId = relation.getId();
        discussion.updated = item.getUpdated();
        discussion.created = item.getCreated();
        discussion.url = getUrlForDiscussion(relation);

        Document data = item.getData();
        Element element = (Element) data.selectSingleNode("/data/comments");
        discussion.responseCount = Misc.parseInt(element.getText(), 0);
        element = (Element) data.selectSingleNode("/data/last_id");
        if (element != null)
            discussion.lastCommentId = Misc.parseInt(element.getText(), discussion.responseCount);
        else
            discussion.lastCommentId = discussion.responseCount;

        discussion.title = removeTags(item.getTitle());
        return discussion;
    }

    public static String getUrlForDiscussion(Relation relation) {
        sync(relation);
        if (relation.getUrl() != null)
            return relation.getUrl();

        if (relation.getParent() instanceof Category) {
            Relation relUpper = (Relation) persistence.findById(new Relation(relation.getUpper()));
            if (relUpper.getUrl() != null)
                return relUpper.getUrl() + "/show/" + relation.getId();
        }

        if (relation.getParent() instanceof Item) {
            Item item = (Item) sync(relation.getParent());

            if (item.getType() == Item.NEWS)
                return "/zpravicky/show/" + relation.getId();
        }

        return "/forum/show/" + relation.getId();
    }

    /**
     * Prepares discussions for displaying.
     * @param content List of Relations containing Items with type=Item.Discussion
     * @return list of PreparedDiscussions.
     */
    public List<DiscussionHeader> analyzeDiscussions(List content) {
        List list = new ArrayList(content.size());
        for ( Iterator iter = content.iterator(); iter.hasNext(); ) {
            DiscussionHeader preparedDiscussion = analyzeDiscussion((Relation) iter.next());
            if ( preparedDiscussion!=null )
                list.add(preparedDiscussion);
        }
        return list;
    }

    /**
     * Tests whether user has seen all comments in given discussion.
     * @param maybeUser instance of User or some undefined value
     * @param diz initialized discussion header
     * @return true if maybeUser is user, which has seen this discussion and there is new comment
     */
    public boolean hasNewComments(Object maybeUser, DiscussionHeader diz) {
        if (maybeUser == null || ! (maybeUser instanceof User))
            return false;
        if (diz.getDiscussion() == null)
            return false;
        int dizId = diz.getDiscussion().getId();
        Integer lastSeen = ((User)maybeUser).getLastSeenComment(dizId);
        if (lastSeen == null)
            return false;
        return lastSeen < diz.getLastCommentId();
    }

    /**
     * Finds, how many comments is in discussion associated with this
     * GenericObject. If there is more than one comment, it sets its time too.
     * @return Map holding info about comments of this object.
     */
    public static DiscussionHeader findComments(GenericObject object) {
        List children = object.getChildren();
        if (children==null)
            return new DiscussionHeader(null);
        for ( Iterator iter = children.iterator(); iter.hasNext(); ) {
            Relation relation = (Relation) iter.next();
            GenericObject child = (relation).getChild();
            if ( !(child instanceof Item) )
                continue;
            Item item = (Item) child;
            if ( !item.isInitialized() )
                persistence.synchronize(item);
            if ( item.getType()!=Item.DISCUSSION )
                continue;
            return analyzeDiscussion(relation);
        }
        return new DiscussionHeader(null);
    }

    /**
     * Creates facade around Item.
     * @param item question
     * @return Comment instance
     */
    public static Comment createComment(Item item) {
        return new ItemComment(item);
    }

    public Map ratingFor(Branch object, String s) {
        return ratingFor(object);
    }

    /**
     * Finds rating of specified type for given object.
     * @param object object that might have rating
     * @return null, if there is no such rating. Otherwise map with keys "sum", "count" and "percent".
     */
    public Map ratingFor(Branch object) {
        return calculatePercentage(object, "//rating", EditRating.VALUE_MAX);
    }

    /**
     * Calculates percentage
     * @param xmlNode XML
     * @param xpath xpath to an element which contains elements sum and count
     * @param divider (100 * sum) / (float)(count * divider)
     * @return null, if there is no such rating. Otherwise map with keys "sum", "count" and "percent".
     */
    public Map calculatePercentage(Branch xmlNode, String xpath, int divider) {
        Element container = (Element) xmlNode.selectSingleNode(xpath);
        if ( container==null )
            return null;

        int sum = Misc.parseInt(container.elementText("sum"), EditRating.VALUE_MIN);
        int count = Misc.parseInt(container.elementText("count"), EditRating.VALUE_MIN);
        float percent = (100 * sum) / (float)(count * divider);

        Map map = new HashMap();
        map.put("sum", sum);
        map.put("count", count);
        map.put("percent", percent);

        return map;
    }

    /**
     * replaces spaces with html nonbreaking spaces in string
     */
    public String nonBreakingSpaces(String s) {
        int length = s.length();
        if (s==null || length==0) return "";
        int i = s.indexOf(' '), j=0;
        if (i==-1) return s;
        StringBuffer sb = new StringBuffer();
        while(i<length && i>-1) {
            sb.append(s.substring(j, i));
            sb.append("&nbsp;");
            j = i+1;
            i = s.indexOf(j, ' ');
        }
        if (i==-1 && j<length)
            sb.append(s.substring(j));
        return sb.toString();
    }

    /**
     * @return string where spaces after one or two characters log words
     * are replaced by non breaking spaces.
     */
    public String vlnka(String s) {
        String modified = new RE(reVlnka, RE.MATCH_MULTILINE).subst(s,vlnkaReplacement,RE.REPLACE_ALL+RE.REPLACE_BACKREFERENCES);
        return modified;
    }

    /**
     * Gets absolute URL for story.
     * @param relation fully initialized relation (including child)
     * @return URL
     */
    public static String getUrlForBlogStory(Relation relation) {
        String url = relation.getUrl();

        if (url != null)
            return url;
        else {
            Category blog = (Category) relation.getParent();
            blog = (Category) persistence.findById(blog);
            Item story = (Item) relation.getChild();
            return getUrlForBlogStory(blog.getSubType(), story.getCreated(), relation.getId());
        }
    }

    /**
     * Gets absolute url for story.
     * @param blogName Name of blog.
     * @param date date when story was published
     * @param relation id of relation of story, 0 if relation and day shoudn't be appended
     * @return formated unique URL. E.g. /blog/leos/archive/2004/12/12/12345
     */
    public static String getUrlForBlogStory(String blogName, Date date, int relation) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        StringBuffer sb = new StringBuffer("/blog/");
        sb.append(blogName);
        sb.append('/');
        sb.append(calendar.get(Calendar.YEAR));
        sb.append('/');
        sb.append(calendar.get(Calendar.MONTH)+1);
        sb.append('/');

        if (relation != 0) {
            sb.append(calendar.get(Calendar.DAY_OF_MONTH));
            sb.append('/');
            sb.append(relation);
        }

        return sb.toString();
    }

    /**
     * Replaces new line characters with space.
     */
    public static String removeNewLines(String text) {
        return new RE(lineBreak, RE.MATCH_MULTILINE).subst(text, " ");
    }

    /**
     * Replaces <p> and </p> with spaces.
     */
    public static String removeParahraphs(String text) {
        return new RE(reRemoveParagraphs, RE.MATCH_MULTILINE).subst(text, " ");
    }

    /**
     * Returns true if object <code>o</code> is a List containing, or a String equal to <code>s</code>.
     */
    public static boolean isWithin(Object o, String s) {
        if ( o==null || s==null )
            return false;

        if ( o instanceof Collection )
            return ((Collection) o).contains(s);

        if ( o instanceof String )
            return o.equals(s);

        return false;
    }

    /**
     * Compares two dates and returns true if their difference is smaller than one second
     * @param one existing date
     * @param two existing date
     * @return false if difference is bigger than one second
     */
    public static boolean isSame(Date one, Date two) {
        long diff = one.getTime() - two.getTime();
        return Math.abs(diff) < 1000;
    }

    /**
     * Returns obj as list. If obj is List, then it is casted
     * to List. If it is collection, then new ArrayList initialized
     * with its entries is returned. If obj is null, empty list
     * is returned. In all other cases list with obj is returned.
     * Warning - returned list may be read-only.
     *
     * @param obj value that can be list
     * @return obj casted to List or List containing obj
     */
    public static List<?> asList(Object obj) {
        if (obj == null)
            return Collections.emptyList();
        if (obj instanceof List)
            return (List<?>) obj;
        if (obj instanceof Collection)
            return new ArrayList((Collection) obj);
        return Collections.singletonList(obj);
    }

    /**
     * Returns obj as set. If obj is Set, then it is casted
     * to Set. If it is collection, then new HashSet initialized
     * with its entries is returned. If obj is null, empty set
     * is returned. In all other cases Set with obj is returned.
     * Warning - returned list may be read-only.
     * @param obj value that can be list
     * @return obj casted to Set or Set containing obj
     */
    public static Set<String> asSet(Object obj) {
        if (obj == null)
            return Collections.emptySet();
        if (obj instanceof Set)
            return (Set<String>) obj;
        if (obj instanceof Collection)
            return new HashSet<String>((Collection) obj);
        return Collections.singleton((String)obj);
    }

    /**
     * Finds all screenshots for given object.
     * @return list of Maps with several keys
     */
    public List<Map> screenshotsFor(GenericDataObject obj) {
        if (obj == null)
            return Collections.EMPTY_LIST;

        Map<String, List<Relation>> byType = groupByType(obj.getChildren(), "Data");
        List<Relation> images = byType.get(Constants.TYPE_DATA);
        return analyzeScreenshots(images);
    }

    /**
     * Finds all screenshots for given object.
     * @return list of Maps with several keys
     */
    public List<Map> analyzeScreenshots(List<Relation> images) {
        if (images == null)
            return Collections.EMPTY_LIST;

        List<Map> result = new ArrayList<Map>();
        for (Iterator iter = images.iterator(); iter.hasNext();) {
            Relation relation = (Relation) iter.next();
            Data data = (Data) relation.getChild();
            Element element = (Element) data.getData().selectSingleNode("/data/object");

            Map map = new HashMap(2, 4.0f);
            map.put("path", element.attributeValue("path"));

            Element thumbnail = element.element("thumbnail");
            if (thumbnail != null)
                map.put("thumbnailPath", thumbnail.attributeValue("path"));

			Element origName = element.element("originalFilename");
			if (origName != null)
				map.put("originalFilename", origName.getText());

			if ("true".equals(element.attributeValue("hidden")))
				continue;

            result.add(map);
        }
        return result;
    }

	/**
     * Finds all attachments for a given object.
     * @return list of Data objects
     */
    public List<Relation> attachmentsFor(GenericDataObject obj) {
        if (obj == null)
            return Collections.emptyList();

        Map<String, List<Relation>> byType = groupByType(obj.getChildren(), "Data");
        List objs = byType.get(Constants.TYPE_DATA);
        if (objs == null)
            return Collections.emptyList();

        return objs;
    }

    /**
     * Initializes discussions to list of relations to objects (articles, news ..)
     * @param objects initialized relations
     */
    public static void initializeDiscussionsTo(List<Relation> objects) {
        initializeChildren(objects);
        List items = new ArrayList();
        for (Iterator<Relation> iter = objects.iterator(); iter.hasNext();) {
            Relation relation1 = iter.next();
            List children = relation1.getChild().getChildren();
            for (Iterator iterIn = children.iterator(); iterIn.hasNext();) {
                Relation relation2 = (Relation) iterIn.next();
                if (relation2.getChild() instanceof Item)
                    items.add(relation2.getChild());
            }
        }
        syncList(items);
    }

    /**
     * Initializes author relations of list of articles
     * @param articles initialized relations
     */
    public static void initializeAuthors(List<Relation> articles) {
        Set authors = new HashSet(articles.size() + 3);
        for (Relation relation : articles) {
            Item article = (Item) relation.getChild();
            Set relationIds = article.getProperty(Constants.PROPERTY_AUTHOR);
            for (Iterator i = relationIds.iterator(); i.hasNext();) {
                int rid = Integer.parseInt((String) i.next());
                authors.add(new Relation(rid));
            }
        }
        syncList(authors);
    }

    /**
     * Initializes children for list of articles. It always loads authors and discussion headers.
     * If articleContent is set to true, then article records are loaded too. If commentsContent
     * is set to true, then even discussion records are loaded from the persistance.
     * @param articles initialized relations
     */
    public static void initializeArticles(List<Relation> articles, boolean articleContent, boolean commentsContent) {
        initializeChildren(articles);

        Set<Relation> authors = new HashSet<Relation>(articles.size() + 3);
        List<Relation> fetchRelations = new ArrayList<Relation>(articles.size() * 3);
        List<Relation> discussionRelations = null;
        if (commentsContent)
            discussionRelations = new ArrayList<Relation>(articles.size());

        for (Relation articleRelation : articles) {
            List<Relation> children = articleRelation.getChild().getChildren();
            for (Relation articleChild : children) {
                if (articleChild.getChild() instanceof Item) { // fetch items, there will be discussion header
                    fetchRelations.add(articleChild);
                    if (commentsContent)
                        discussionRelations.add(articleChild);
                }
                if (articleContent && articleChild.getChild() instanceof Record) // fetch article content
                    fetchRelations.add(articleChild);
            }

            Item article = (Item) articleRelation.getChild();
            Set<String> relationIds = article.getProperty(Constants.PROPERTY_AUTHOR);
            for (String id : relationIds) {
                int rid = Integer.parseInt(id);
                authors.add(new Relation(rid));
            }
        }

        fetchRelations.addAll(authors);
        syncList(fetchRelations);

        fetchRelations.clear();
        if (commentsContent) {
            initializeChildren(discussionRelations);
            for (Relation dizRelation : discussionRelations) {
                if ( ! (dizRelation.getChild() instanceof Item))
                    continue;
                Item item = (Item) dizRelation.getChild();
                if (item.getType() != Item.DISCUSSION)
                    continue;
                fetchRelations.addAll(item.getChildren()); // fetch record relations with comments
            }
            syncList(fetchRelations);
        }
    }

    /**
     * Stores new editor instance in rich text editor
     * @param rte rich text editor
     * @param textAreaId id of associated text area
     * @param formId if od associated form
     */
    public static void addRichTextEditor(RichTextEditor rte, String textAreaId, String formId, String menu) {
        addRichTextEditor(rte, textAreaId, formId, menu, null);
    }

    /**
     * Stores new editor instance in rich text editor
     * @param rte rich text editor
     * @param textAreaId id of associated text area
     * @param formId if od associated form
     * @param commentedText optional HTML containing commented text, used for quotations
     */
    public static void addRichTextEditor(RichTextEditor rte, String textAreaId, String formId, String menu, String commentedText) {
        RichTextEditor.EditorInstance editor = rte.addInstance(textAreaId, formId, menu);
        if (commentedText != null)
            editor.setCommentedContent(commentedText);
    }

    /**
     * Finds relations to all authors of given <code>article</code>.
     * @return List of Authors
     */
    public List createAuthorsForArticle(Item article) {
        Set relationIds = article.getProperty(Constants.PROPERTY_AUTHOR);
        List authors = new ArrayList(relationIds.size());
        for (Iterator i = relationIds.iterator(); i.hasNext(); ) {
            int rid = Integer.parseInt((String) i.next());
            authors.add(new Relation(rid));
        }
        syncList(authors);

        return authors;
    }

    /**
     * Decides if item is question in forum or not
     * @param relation relation containing item
     * @return true if it is question
     */
    public static boolean isQuestion(Relation relation) {
        return isQuestion((Item) relation.getChild());
    }

    /**
     * Decides if item is question in forum or not
     * @param item discussion item
     * @return true if it is question
     */
    public static boolean isQuestion(Item item) {
        if (item.getType() != Item.DISCUSSION)
            return false;
        return item.getSubType() != null;
    }

    /**
     * Tests, whether speficied user is watching given document. If maybeUser is not User instance, false is returned.
     * @param obj Item or Category, it must be initialized
     * @param maybeUser this may be instance of User
     * @return true if current user is monitoring this object
     */
    public static boolean isMonitored(GenericDataObject obj, Object maybeUser) {
        if (obj.getMonitorCount() == 0)
            return false;

        User user = null;
        if (maybeUser instanceof User)
            user = (User) maybeUser;
        if (user == null)
            return false;

        Set<Integer> users = MonitorTool.get(obj);
        if (users == null || users.isEmpty())
            return false;

        return users.contains(new Integer(user.getId()));
    }

	public static Permissions permissionsFor(Object anUser, Relation rel) {
        User user = null;
        if (anUser instanceof User)
            user = (User) anUser;
        if (user != null && user.hasRole(Roles.ROOT))
			return Permissions.PERMISSIONS_ROOT;

		sync(rel);
		GenericObject obj = rel.getChild();
		if (obj instanceof GenericDataObject) {
			GenericDataObject gdo = (GenericDataObject) obj;
			int permissions, shift;

			if ( user != null && user.isMemberOf(gdo.getGroup()) )
				shift = Permissions.PERMISSIONS_GROUP_SHIFT;
			else
				shift = Permissions.PERMISSIONS_OTHERS_SHIFT;

			permissions = gdo.getPermissions();
			if (obj instanceof Category)
				permissions &= ~Permissions.PERMISSIONS_CATEGORY_MASK;

			return new Permissions( (permissions >> shift) & 0xff );
		}
		return new Permissions(0);
	}

	public static Permissions permissionsFor(Object user, int rel) {
		return permissionsFor(user, new Relation(rel));
	}

    /**
     * Returns the RID and the question count for every forum that should be displayed on the HP.
     * @param user
     * @return
     */
    public static Map<Integer, Integer> getUserForums(User user) {
        VariableFetcher vars = VariableFetcher.getInstance();
        Map<Integer, Integer> mainForums = vars.getMainForums();
        Map<Integer, Integer> retval = new LinkedHashMap<Integer, Integer>(mainForums.size());

        retval.putAll(mainForums);

        if (user == null)
            return retval;

        List<Element> elements = user.getData().selectNodes("/data/forums/forum");
        if (elements == null)
            return retval;

        for (Element elem : elements) {
            int rid = Misc.parseInt(elem.getText(), 0);
            retval.put(rid, Misc.parseInt(elem.attributeValue("questions"), vars.getDefaultSizes().get(VariableFetcher.KEY_QUESTION)));
        }

        return retval;
    }

    /**
     * Detects whether the text of a news item is too long to be displayed on the HP. If text contains more
     * than newsLetterHardLimit characters, then shorter version consisting of newsLetterSoftLimit
     * characters is returned. Only words are counted, not whitespace, end tags are not skipped. If text contains
     * P or BR tags, then text before them is returned. The text may start with P tag.
     * @param text The text to be checked
     * @return Null if the text is short enough or the shortened text.
     */
    public static String limitNewsLength(String text) {
        try {
            Lexer lexer = new Lexer(text);
            org.htmlparser.Node node;
            TagNode tag;
            int count = 0;
            boolean shortened = false, oversized = false, textFound = false, breakFound = false;
            StringBuilder sb = new StringBuilder(text.length());
            while ((node = lexer.nextNode()) != null) {
                if ((node instanceof Text)) {
                    if ( ! oversized) {
                        StringTokenizer stk = new StringTokenizer(node.getText(), " \t\n\r\f", true);
                        while (stk.hasMoreTokens()) {
                            String s = stk.nextToken();
                            if (! Character.isWhitespace(s.charAt(0))) {
                                textFound = true;
                                count += s.length();
                                if (! shortened) {
                                    if (count <= newsLetterSoftLimit)
                                        sb.append(s);
                                    if (count >= newsLetterSoftLimit)
                                        shortened = true;
                                }
                                if (count > newsLetterHardLimit) {
                                    oversized = true;
                                    break;
                                }
                            } else if (! shortened)
                                sb.append(s);
                        }
                    }
                } else if (node instanceof TagNode) {
                    tag = (TagNode) node;
                    String tagName = tag.getTagName();
                    if (textFound && ! tag.isEndTag() && ("P".equals(tagName) || "BR".equals(tagName))) {
                        breakFound = true;
                        break;
                    }
                    sb.append(tag.toHtml());
                }
            }
            return (oversized || breakFound) ? sb.toString() : null;
        } catch (ParserException e) {
            log.error("Failed to shorten news '" + text + "'!");
            return null;
        }
    }

    /**
     * Parses the text. Any inline objects are detected and handled accordingly.
     * @param text The text of the article to be processed
     * @return list of maps with keys "type" and "value"
     */
    public static List<Map> processArticle(String text) {
        RE regexpPolls = new RE(reInlineObject, RE.MATCH_MULTILINE);
        int pos = 0;
        List<Map> items = new ArrayList<Map>(3);

        while (true) {
            if (regexpPolls.match(text, pos)) {
                Map<String,String> map;

                // add the preceding text
                map = new HashMap<String, String>(2);
                map.put("type", "text");
                map.put("value", text.substring(pos, regexpPolls.getParenStart(0)));
                items.add(map);

                pos = regexpPolls.getParenEnd(0);

                String type = regexpPolls.getParen(1);
                if ("poll".equals(type) || "video".equals(type)) {
                    // add a poll or video
                    map = new HashMap<String, String>(2);
                    map.put("type", type);
                    map.put("value", regexpPolls.getParen(2));
                    items.add(map);
                }
            } else
                break;
        }

        // add the remaining text
        if (pos < text.length()) {
            Map<String,String> map = new HashMap<String, String>(2);
            map.put("type", "text");
            map.put("value", text.substring(pos));
            items.add(map);
        }

        return items;
    }

    public static Map<String,String> getOfferedCssStyles() {
        return offeredCssStyles;
    }

    public static String processLocalLinks(String text, Relation discussion) {
        try {
            RE regexp = new RE(reDetectLink, RE.MATCH_MULTILINE);
            int i = 0;
            StringBuffer sb = new StringBuffer();

            while (regexp.match(text, i)) {
                String link = regexp.getParen(1);
                int hash = link.indexOf('#');
                boolean replaced = false;

                sb.append(text.substring(i, regexp.getParenStart(0)));
                sb.append("<a href=\"");

                if (discussion != null && hash != -1) {
                    String prehash = link.substring(0, hash);
                    String upper = null;

                    if (discussion.getParent() instanceof Item) {
                        Relation upperRel = (Relation) persistence.findById(new Relation(discussion.getUpper()));
                        upper = upperRel.getUrl();
                    }

                    if (prehash.equals(Tools.getUrlForDiscussion(discussion)) || prehash.equals(upper)) {
                        boolean isdigit = false;

                        for (int j = hash+1; j < link.length(); j++) {
                            if (!Character.isDigit(link.charAt(j))) {
                                isdigit = false;
                                break;
                            } else
                                isdigit = true;
                        }

                        if (isdigit) {
                            sb.append("#"+Integer.parseInt(link.substring(hash+1)));
                            replaced = true;
                        }
                    }
                }

                if (!replaced)
                    sb.append(link);

                i = regexp.getParenEnd(1);
            }

            sb.append(text.substring(i));

            return sb.toString();
        } catch (Exception e) { // just in case
            log.warn("processLocalLinks failed on '" + text + "'", e);
            return text;
        }
    }

    public static void setStoryReservePercents(int storyReservePercents) {
        Tools.storyReservePercents = storyReservePercents;
    }

    public static void setNewsLetterSoftLimit(int newsLetterSoftLimit) {
        Tools.newsLetterSoftLimit = newsLetterSoftLimit;
    }

    public static void setNewsLetterHardLimit(int newsLetterHardLimit) {
        Tools.newsLetterHardLimit = newsLetterHardLimit;
    }

    public static Map getStandardAdRegexps() {
        return Advertisement.standardRegexps;
    }

    public static String fixAmpersand(String url) {
        if (url==null || url.length()==0)
	        return url;
        Matcher matcher = ampersand.matcher(url);
	    return matcher.replaceAll("&amp;");
    }

    public static String prefixAbsoluteLinks(String text, String prefix) {
        return text.replaceAll("<a href=\"/", "<a href=\""+prefix+"/");
    }
}
