/*
 * HTMLHyperlinkSource.java - Hyperlink source from the XML plugin
 * :tabSize=4:indentSize=4:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2011 Eric Le Lay
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package xml.hyperlinks;

import static xml.Debug.DEBUG_HYPERLINKS;
import gatchan.jedit.hyperlinks.FallbackHyperlinkSource;
import gatchan.jedit.hyperlinks.Hyperlink;
import gatchan.jedit.hyperlinks.HyperlinkSource;
import gatchan.jedit.hyperlinks.jEditOpenFileHyperlink;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.tree.DefaultMutableTreeNode;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

import sidekick.IAsset;
import sidekick.enhanced.SourceAsset;
import sidekick.html.parser.html.HtmlDocument.Attribute;
import sidekick.html.parser.html.HtmlDocument.AttributeList;
import sidekick.html.parser.html.HtmlDocument.HtmlElement;
import sidekick.html.parser.html.HtmlDocument.Tag;
import sidekick.html.parser.html.HtmlDocument.TagBlock;
import sidekick.html.parser.html.HtmlVisitor;
import sidekick.util.ElementUtil;
import sidekick.util.Location;
import sidekick.util.SideKickAsset;
import sidekick.util.SideKickElement;
import xml.Resolver;
import xml.XmlParsedData;
import xml.completion.ElementDecl;
import xml.completion.IDDecl;
import xml.parser.XmlTag;

/**
 * Provides hyperlinks from HTML attributes.
 * Supported hyperlinks are all attributes with type URI
 * in the HTML 4.01 spec.
 * Links to other documents and anchors inside document
 * are supported, but fragment identifiers in other documents
 * are not.
 * the HTML/HEAD/BASE element is used to resolve URIs
 * if present.
 * No reparsing is required, contrary to XMLHyperlinkSource
 * which reparses the start tag.
 *
 * @author Eric Le Lay
 * @version $Id: HTMLHyperlinkSource.java 24186 2015-12-05 15:53:56Z daleanson $
 */
public class HTMLHyperlinkSource implements HyperlinkSource
{
	/**
	 * Returns the hyperlink for the given offset.
	 * returns an hyperlink as soon as pointer enters the attribute's value
	 *
	 * @param buffer the buffer
	 * @param offset the offset
	 * @return the hyperlink (or null if there is no hyperlink)
	 */
	public Hyperlink getHyperlink(Buffer buffer, int offset){
		View view = jEdit.getActiveView();
		XmlParsedData data = XmlParsedData.getParsedData(view, false);
		if(data==null)return null;
		
		IAsset asset = data.getAssetAtOffset(offset);
		if(asset == null){
			Log.log(Log.DEBUG, HTMLHyperlinkSource.class,"no Sidekick asset here");
			return null;
		} else if(asset instanceof SideKickAsset){
			int wantedLine = buffer.getLineOfOffset(offset);
			int wantedLineOffset = buffer.getVirtualWidth(wantedLine, offset - buffer.getLineStartOffset(wantedLine));

			SideKickElement elt = ((SideKickAsset)asset).getElement();
			
			Tag startTag;
			if(elt instanceof TagBlock){
				startTag = ((TagBlock)elt).startTag;
			}else if(elt instanceof Tag){
				startTag = (Tag)elt;
			}else{
				Log.log(Log.WARNING,HTMLHyperlinkSource.class,"unexpected asset type: "+elt.getClass()+", please report");
				startTag = null;
				return null;
			}

			int end= ElementUtil.createEndPosition(buffer,startTag).getOffset();
			/* if the offset is inside start tag */
			if(offset <= end)
			{
				AttributeList al = startTag.attributeList;
				if(al == null){
					if(DEBUG_HYPERLINKS)Log.log(Log.DEBUG,HTMLHyperlinkSource.class,"no attribute in this element");
					return null;
				}else{
					for(Attribute att: al.attributes){
						// offset is inside attribute's value
						if(  (    att.getValueStartLocation().line < wantedLine+1
						       || (att.getValueStartLocation().line == wantedLine+1
							   && att.getValueStartLocation().column <= wantedLineOffset)
						      )
						      &&
						      (    att.getEndLocation().line > wantedLine+1
						       || (att.getEndLocation().line == wantedLine+1
							   && att.getEndLocation().column > wantedLineOffset)
						      )
						  )
						{
							return getHyperlinkForAttribute(buffer, offset, data, startTag, att);
						}
					}
					if(DEBUG_HYPERLINKS)Log.log(Log.DEBUG,HTMLHyperlinkSource.class,"not inside attributes");
					return null;
				}
			}else{
				return null;
			}
		} else if(asset instanceof XmlTag){
			// it can be the case that an .html buffer is parsed
			// as XML (e.g. when the HTML should be transformed using
			// XSLT, it must be valid XML, so parsed with the xml parser
			// to acertain that).
			// Since the source is based on the mode, it will be still
			// the HTMLHyperlikSource even if the Sidekick tree is for XML.
			return sourceForXML.getHyperlink(buffer, offset, data, (XmlTag) asset, true);
		} else if(asset instanceof SourceAsset){
			// document root, no hyperlink there
			return null;
		} else {
			Log.log(Log.WARNING,HTMLHyperlinkSource.class,"unexpected asset type: "+asset.getClass()+", please report");
			return null;
		}
	}
	
	/**
	 * get an hyperlink for an identified HTML attribute
	 * @param	buffer	current buffer
	 * @param	offset	offset where an hyperlink is required in current buffer
	 * @param	data		sidekick tree for current buffer
	 * @param	startTag		element containing offset
	 * @param	att		parsed attribute
	 */
	public Hyperlink getHyperlinkForAttribute(
		Buffer buffer, int offset, XmlParsedData data, 
		Tag startTag, Attribute att)
	{
		String tagLocalName = startTag.tagName;
		
		String localName = att.getName();
		String value = att.getValue();
		boolean quoted;
		if((value.startsWith("\"")&&value.endsWith("\""))
			||(value.startsWith("'")&&value.endsWith("'")))
		{
			value = value.substring(1,value.length()-1);
			quoted = true;
		}else{
			quoted = false;
		}
		
		
		Hyperlink h = getHyperlinkForAttribute(buffer, offset,
			tagLocalName, localName, value,
			data, startTag, att, quoted);
		
		if(h == null) {
		
			ElementDecl eltDecl  = data.getElementDecl(localName,offset);
			if(eltDecl == null){
				if(DEBUG_HYPERLINKS)Log.log(Log.DEBUG,HTMLHyperlinkSource.class,"no element declaration for "+tagLocalName);
			}else{
				ElementDecl.AttributeDecl attDecl = eltDecl.attributeHash.get(localName);
				if(attDecl == null){
					if(DEBUG_HYPERLINKS)Log.log(Log.DEBUG,HTMLHyperlinkSource.class,"no attribute declaration for "+localName);
					return null;
				}else{
					if("IDREF".equals(attDecl.type)){
						return getHyperlinkForIDREF(buffer, data, value, att, quoted);
					}else if("anyURI".equals(attDecl.type)){
						String href =  resolve(value, buffer, offset, data);
						if(href!=null){
							return newJEditOpenFileHyperlink(buffer, att, href, quoted);
						}
					}else if("IDREFS".equals(attDecl.type)){
						return getHyperlinkForIDREFS(buffer, offset, data, value, att, quoted);
					}
					return null;
				}
			}
			
		} else {
			return h;
		}
		return null;
	}
	
	/**
	 * creates an hyperlink to the location of the element with id (in same or another buffer).
	 * @param	buffer	current buffer
	 * @param	data		sidekick tree
	 * @param	id		id we are looking for
	 * @param	att		parsed attribute (for hyperlink boundaries)
	 * @param	quoted	is the value inside quotes ?
	 */
	public Hyperlink getHyperlinkForIDREF(Buffer buffer,
		XmlParsedData data, String id, Attribute att,
		boolean quoted)
	{
		IDDecl idDecl = data.getIDDecl(id);
		if(idDecl == null){
			return null;
		} else{
			return newJEditOpenFileAndGotoHyperlink(buffer, att,
			idDecl.uri, idDecl.line, idDecl.column, quoted);
		}
	}
	
	// {{{ getHyperlinkForIDREFS() method
	private static final Pattern noWSPattern = Pattern.compile("[^\\s]+");
	/**
	 * creates an hyperlink to the location of the element with id (in same or another buffer)
	 * @param	buffer	current buffer
	 * @param	offset	offset of required hyperlink
	 * @param	data		sidekick tree
	 * @param	attValue		ids in the attribute
	 * @param	att		parsed attribute (for hyperlink boundaries)
	 * @param	quoted	is the value inside quotes ?
	 */
	public Hyperlink getHyperlinkForIDREFS(Buffer buffer, int offset,
		XmlParsedData data, String attValue, Attribute att,
		boolean quoted)
	{
		int attStart = xml.ElementUtil.createOffset(buffer, att.getValueStartLocation());
		// +1 for the quote around the attribute value
		if(quoted)attStart++;
		
		Matcher m = noWSPattern.matcher(attValue);
		while(m.find()){
			int st = m.start(0);
			int nd = m.end(0);
			if(attStart + st <= offset && attStart + nd >= offset){
				IDDecl idDecl = data.getIDDecl(m.group(0));
				if(idDecl==null)return null;
				int start = attStart + st;
				int end= attStart + nd;
				int line = buffer.getLineOfOffset(start);
				return new jEditOpenFileAndGotoHyperlink(start, end, line, idDecl.uri, idDecl.line, idDecl.column);
			}
		}
		return null;
	}//}}}
	
	//{{{ getHyperlinkForAttribute(tagName,attName) method
	private static final Map<String,Set<String>> uriAttributes = new HashMap<String,Set<String>>();
	static{
		HashSet<String> h;
		
		h = new HashSet<String>();
		h.add("href");
		uriAttributes.put("a", h);
		uriAttributes.put("area", h);
		uriAttributes.put("link", h);
		
		h = new HashSet<String>();
		h.add("longdesc");
		h.add("usemap");
		uriAttributes.put("img", h);
		
		h = new HashSet<String>();
		h.add("cite");
		uriAttributes.put("q", h);
		uriAttributes.put("blockquote", h);
		uriAttributes.put("ins", h);
		uriAttributes.put("del", h);
		
		h = new HashSet<String>();
		h.add("usemap");
		uriAttributes.put("input", h);
		uriAttributes.put("object", h);
		
		h = new HashSet<String>();
		h.add("src");
		uriAttributes.put("script", h);
	}
	
	/**
	 * recognize hyperlink attributes by their parent element's
	 * name and/or their name.
	 */
	public Hyperlink getHyperlinkForAttribute(Buffer buffer, int offset,
		String tagLocalName,
		String attLocalName, String attValue,
		XmlParsedData data, Tag tag, Attribute att,
		boolean quoted)
	{
		// +1 for the quote around the attribute value
		int attStart = xml.ElementUtil.createOffset(buffer, att.getValueStartLocation()) +1;

		IsHyperLink h = isHyperlinkAttribute(buffer, offset, 
				tagLocalName, attLocalName, attStart, attValue);
		
		if(h != null)
		{
			if(h.href.startsWith("#")){
				Location found = getNamedAnchorLocation(data, h.href.substring(1));
				if(found != null){
					int attLine = buffer.getLineOfOffset(h.start);
					// OpenFileAndGoto expects real line&column,
					// not virtual column
					int toffset = xml.ElementUtil.createOffset(buffer, found);
					int line = buffer.getLineOfOffset(toffset);
					int column = toffset - buffer.getLineStartOffset(line);
					// it's OK to have a path
					String href = buffer.getPath();
					return new jEditOpenFileAndGotoHyperlink(h.start,
							h.end, attLine, href, line, column);
				}
			}else{
				String href = h.href;
				
				if(h.resolveAgainst != null){
					href = resolveRelativeTo(href,
							tag.getAttributeValue(h.resolveAgainst));
				}
				
				href = resolve(href, buffer, offset, data);
				if(href==null){
					return null;
				}else{
					int attLine = buffer.getLineOfOffset(h.start);
					return new jEditOpenFileHyperlink(h.start, h.end, attLine, href);
				}
			}
		}
		return null;
	}//}}}
	
	/**
	 * recognize hyperlink attributes by their parent element's
	 * namespace:localname and/or their namespace:localname
	 */
	public static IsHyperLink isHyperlinkAttribute(Buffer buffer, int offset,
		String tagLocalName, String attLocalName, 
		int attStart, String attValue)
	{
		if(uriAttributes.containsKey(tagLocalName)
			&& uriAttributes.get(tagLocalName).contains(attLocalName))
		{
			return new IsHyperLink(attStart, attStart + attValue.length(),
					attValue, null, false);
		} else if("object".equals(tagLocalName)
			&& ("classid".equals(attLocalName)
				|| "data".equals(attLocalName)))
		{
			return new IsHyperLink(attStart, attStart + attValue.length(),
					attValue, "codebase", false);
		} else if("object".equals(tagLocalName)
			&& "archive".equals(attLocalName))
		{
			Matcher m = noWSPattern.matcher(attValue);
			while(m.find()){
				int st = m.start(0);
				int nd = m.end(0);
				if(attStart + st <= offset && attStart + nd >= offset){
					String href = m.group(0);
					int start = attStart + st;
					int end= attStart + nd;
					return new IsHyperLink(start, end, href, 
							"codebase", false);
				}
			}
		}
		return null;
	}//}}}

	//{{{ resolveRelativeTo(href,base) methode
	/**
	 * resolves using URI.resolve() href against base
	*/	
	static String resolveRelativeTo(String href, String base){
		if(base == null || "".equals(base))return href;
		
		try{
			return URI.create(base).resolve(href).toString();
		}catch(IllegalArgumentException iae){
			Log.log(Log.WARNING,HTMLHyperlinkSource.class,"error resolving against base",iae);
			return href;
		}
	}//}}}
	
	//{{{ resolve(uri, buffer) method
	/**
	 * resolve a potentially relative uri using HTML BASE element,
	 * the buffer's URL, xml.Resolver.
	 * Has the effect of opening the cached document if it's in cache (eg. docbook XSD if not in catalog).
	 * Maybe this is not desirable, because if there is a relative link in this document, it won't work
	 * because the document will be .jedit/dtds/cachexxxxx.xml and not the real url.
	 *
	 * @param	uri		text of uri to reach
	 * @param	buffer	current buffer
	 * @param	offset	offset in current buffer where an hyperlink is required
	 * @param	data		SideKick parsed data
	 *
	 * @return	resolved URL
	 */
	public String resolve(String uri, Buffer buffer, int offset, XmlParsedData data)
	{
		String href = null;
		String base = xml.PathUtilities.pathToURL(buffer.getPath());
		
		// {{{ use html/head/base
		TagBlock html = getHTML(data);
		if(html != null
			&& html.body.size()>0)
		{
			for(Iterator ith =  ((TagBlock)html).body.iterator();ith.hasNext();){
				HtmlElement head = (HtmlElement) ith.next();
				if(head instanceof TagBlock){
					if("head".equalsIgnoreCase(((TagBlock)head).startTag.tagName))
					{
						for(Iterator it =  ((TagBlock)head).body.iterator();it.hasNext();){
							HtmlElement e = (HtmlElement)it.next();
							if(e instanceof Tag
								&& "base".equalsIgnoreCase(((Tag)e).tagName))
							{
								// Base must be absolute in HTML 4.01
								String preBase = ((Tag)e).getAttributeValue("href");
								try{
									// add a dummy component, otherwise xml.Resolver
									// removes the last part of the xml:base
									// FIXME: review xml.Resolver : it should only be used with URLs
									// for current, now, so could use URI.resolve() instead of removing
									// last part of the path to get the parent...
									base = URI.create(preBase).resolve("dummy").toString();
								}catch(IllegalArgumentException iae){
									Log.log(Log.WARNING, XMLHyperlinkSource.class, "error resolving uri", iae);
								}
								break;
							}
						}
					}
					break;
				}
			}
		}//}}}
		
		try{
			href = Resolver.instance().resolveEntityToPath(
				"", /*name*/
				"", /*publicId*/
				base, /*current, augmented by xml:base */
				uri);
		}catch(java.io.IOException ioe){
			Log.log(Log.ERROR,XMLHyperlinkSource.class,"error resolving href="+uri,ioe);
		}
		return href;
	}//}}}
	
	/**
	 * create an hyperlink for attribute att.
	 * the hyperlink will span whole attribute value
	 * @param	buffer	current buffer
	 * @param	att		parsed attribute
	 * @param	href		uri to open
	 * @param	quoted	is the value inside quotes ?
	 */
	public Hyperlink newJEditOpenFileHyperlink(
		Buffer buffer, Attribute att, String href,
		boolean quoted)
	{
		int start = xml.ElementUtil.createOffset(buffer,att.getValueStartLocation());
		int end= xml.ElementUtil.createOffset(buffer,att.getEndLocation());
		if(quoted){
			start++;
			end--;
		}
        	int line = buffer.getLineOfOffset(start);
        	return new jEditOpenFileHyperlink(start, end, line, href);
	}

	/**
	 * create an hyperlink for attribute att.
	 * the hyperlink will span whole attribute value
	 * @param	buffer	current buffer
	 * @param	att		parsed attribute
	 * @param	href		uri to open
	 * @param	gotoLine	target line in buffer
	 * @param	gotoCol	target column in buffer
	 * @param	quoted	is the value inside quotes ?
	 */
	public Hyperlink newJEditOpenFileAndGotoHyperlink(
		Buffer buffer, Attribute att, String href, int gotoLine, int gotoCol,
		boolean quoted)
	{
		int start = xml.ElementUtil.createOffset(buffer,att.getValueStartLocation());
		int end= xml.ElementUtil.createOffset(buffer,att.getEndLocation());
		if(quoted){
			start++;
			end--;
		}
        	int line = buffer.getLineOfOffset(start);
        	return new jEditOpenFileAndGotoHyperlink(start, end, line, href, gotoLine, gotoCol);
	}

	TagBlock getHTML(XmlParsedData data) {
		DefaultMutableTreeNode tn = (DefaultMutableTreeNode)data.root;
		
		DefaultMutableTreeNode docRoot = (DefaultMutableTreeNode)tn.getFirstChild();
		
		if(docRoot == null){
			if(DEBUG_HYPERLINKS)Log.log(Log.WARNING,HTMLHyperlinkSource.class,"not parsed ??");
			return null;
		}else{
			SideKickElement elt = ((SideKickAsset)data.getAsset(docRoot)).getElement();
			if(elt instanceof TagBlock){
				return (TagBlock)elt;
			}
		}
		return null;
	}
	
	private static class FoundException extends RuntimeException{}
	private static final FoundException foundException = new FoundException();
	
	private static final class NamedAnchorVisitor extends HtmlVisitor{
		private final String searchedAnchor;
		Location foundLoc;
		
		NamedAnchorVisitor(String searched){
			searchedAnchor = searched;
			foundLoc = null;
		}
		
		public void visit(Tag t) {
			String v = null;
			if("a".equalsIgnoreCase(t.tagName)){
				 v = t.getAttributeValue("name");
			}
			if(v == null){
				v = t.getAttributeValue("id");
			}
			if(searchedAnchor.equals(v)){
				foundLoc = t.getStartLocation();
				throw foundException;
			}
		}
	}
	
	public Location getNamedAnchorLocation(XmlParsedData data, String name){
		TagBlock html = getHTML(data);
		if(html != null){
			NamedAnchorVisitor v = new NamedAnchorVisitor(name);
			try{
				html.accept(v);
			}catch(FoundException e){
				return v.foundLoc;
			}
		}
		return null;
	}

	public static class IsHyperLink{
		public final int start;
		public final int end;
		public final String href;
		public final String resolveAgainst;
		public final boolean resolveAgainstXMLBase;
		
		public IsHyperLink(int start, int end, String href,
				String resolveAgainst,
				boolean resolveAgainstXMLBase) {
			this.start = start;
			this.end = end;
			this.href = href;
			this.resolveAgainst = resolveAgainst;
			this.resolveAgainstXMLBase = resolveAgainstXMLBase;
		}
	}
	
	private XMLHyperlinkSource sourceForXML = new XMLHyperlinkSource();
	
	public static HyperlinkSource create(){
		return new FallbackHyperlinkSource(
			Arrays.asList(new HTMLHyperlinkSource(),
				new gatchan.jedit.hyperlinks.url.URLHyperlinkSource()));
	}
}
