/*
 * VFSFileListTest.java
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2010 Eric Le Lay
 *
 * The XML plugin is licensed under the GNU General Public License, with
 * the following exception:
 *
 * "Permission is granted to link this code with software released under
 * the Apache license version 1.1, for example used by the Xerces XML
 * parser package."
 */
package xml.gui;

// {{{ jUnit imports 
import static org.fest.assertions.Assertions.assertThat;
import static org.gjt.sp.jedit.testframework.TestUtils.findDialogByTitle;
import static org.gjt.sp.jedit.testframework.TestUtils.view;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;

import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.Containers;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.FrameFixture;
import org.fest.swing.timing.Pause;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.testframework.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
// }}}

/**
 * unit tests for VFSFileList
 * $Id: VFSFileListTest.java 22004 2012-08-15 10:10:46Z kerik-sf $
 */
public class VFSFileListTest{
	private static File testData;
	
    @BeforeClass
    public static void setUpjEdit() throws IOException{
        TestUtils.beforeClass();
        testData = new File(System.getProperty("test_data")).getCanonicalFile();
        assertTrue(testData.exists());
    }
    
    @AfterClass
    public static void tearDownjEdit() {
        TestUtils.afterClass();
    }
    
    @Test
    public void testEnabledDisabled() throws IOException{
    	
    	final VFSFileList selector = new VFSFileList(view(),"xml.translate.xml-inputs");
    	selector.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
    	
    	final FrameFixture frame = new FrameFixture(TestUtils.robot(),Containers.frameFor(selector));
    	frame.show();
    	
		frame.list("xml.translate.xml-inputs").requireEnabled();
		frame.button("xml.translate.xml-inputs.add").requireEnabled();
		
		GuiActionRunner.execute(new GuiTask(){
				protected void executeInEDT(){
					selector.setEnabled(false);
				}
		});
		frame.list("xml.translate.xml-inputs").requireDisabled();
		frame.button("xml.translate.xml-inputs.add").requireDisabled();
		
		GuiActionRunner.execute(new GuiTask(){
				protected void executeInEDT(){
					selector.setEnabled(true);
				}
		});
		frame.list("xml.translate.xml-inputs").requireEnabled();
		frame.button("xml.translate.xml-inputs.add").requireEnabled();

		frame.close();
    }
    
    
    @Test
    public void testAPI() throws IOException{
    	final File f = new File(testData, "simple/actions.xml");
    	
    	final VFSFileList selector = new VFSFileList(view(),"xml.translate.xml-inputs");
    	selector.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
    	
    	final FrameFixture frame = new FrameFixture(TestUtils.robot(),Containers.frameFor(selector));
    	frame.show();
    	
		assertFalse(selector.itemsExist());
		assertEquals(0,selector.getItemCount());
		assertNull(selector.getSelectedItem());

		GuiActionRunner.execute(new GuiTask(){
				protected void executeInEDT(){
					selector.setItems(new String[]{f.getPath()});
				}
		});
		
		assertThat(frame.list("xml.translate.xml-inputs").contents()).containsOnly(f.getPath());
		assertTrue(selector.itemsExist());
		assertEquals(1,selector.getItemCount());
		assertThat(frame.list("xml.translate.xml-inputs").contents()).isEqualTo(selector.getItems());
		assertNull(selector.getSelectedItem());
		
		try{
			selector.getItem(2);
			fail("Should throw an IllegalArgumentException");
		}catch(IllegalArgumentException e){
			//pass
		}
		try{
			selector.getItem(-1);
			fail("Should throw an IllegalArgumentException");
		}catch(IllegalArgumentException e){
			//pass
		}
		
		frame.close();
    }

    @Test
    public void testOpenFile(){
    	final File f = new File(testData, "simple/actions.xml");
    	final VFSFileList selector = new VFSFileList(view(),"xml.translate.xml-inputs");
    	selector.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
    	
		assumeTrue(!view().getBuffer().getPath().equals(f.getPath()));

    	final FrameFixture frame = new FrameFixture(TestUtils.robot(),Containers.frameFor(selector));
    	frame.show();
    	
		GuiActionRunner.execute(new GuiTask(){
				protected void executeInEDT(){
					selector.setItems(new String[]{f.getPath()});
				}
		});
		frame.list("xml.translate.xml-inputs").selectItem(0);
		frame.list("xml.translate.xml-inputs").showPopupMenu().menuItemWithPath("Open file").click();
		
		assertEquals(f.getPath(),view().getBuffer().getPath());
		
		frame.close();
		jEdit.closeBuffer(view(),view().getBuffer());
    }
    
    @Test
    public void testAddRemove(){
    	final File xml = new File(testData, "simple/actions.xml");
    	File xsd = new File(testData, "simple/actions.xsd");
    	final VFSFileList selector = new VFSFileList(view(),"xml.translate.xml-inputs");
    	selector.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
    	final FrameFixture frame = new FrameFixture(TestUtils.robot(),Containers.frameFor(selector));
     	frame.show();
   	
		GuiActionRunner.execute(new GuiTask(){
				protected void executeInEDT(){
					selector.setItems(new String[]{xml.getPath()});
				}
		});

		frame.list("xml.translate.xml-inputs").showPopupMenu().menuItemWithPath("Add").click();
		
		DialogFixture browseDialog = findDialogByTitle("File Browser - Open");
		Pause.pause(1000);
		browseDialog.button("up").click();
		Pause.pause(1000);
		browseDialog.table("file").cell(
			browseDialog.table("file").cell(xsd.getParentFile().getName())).doubleClick();
		Pause.pause(1000);
		browseDialog.table("file").selectCell(
			browseDialog.table("file").cell(xsd.getName()));
		browseDialog.button("ok").click();

		assertThat(frame.list("xml.translate.xml-inputs").contents()).contains(xsd.getPath());
		
		frame.list("xml.translate.xml-inputs").selectItem(0);
		
		frame.button("xml.translate.xml-inputs.add").click();
		
		browseDialog = findDialogByTitle("File Browser - Open");
		Pause.pause(1000);
		browseDialog.button("up").click();
		Pause.pause(1000);
		browseDialog.table("file").cell(
			browseDialog.table("file").cell(xml.getParentFile().getName())).doubleClick();
		Pause.pause(1000);
		browseDialog.table("file").selectCell(
			browseDialog.table("file").cell(xml.getName()));
		browseDialog.button("ok").click();

		assertThat(frame.list("xml.translate.xml-inputs").contents()).contains(xml.getPath());
		assertEquals(3,frame.list("xml.translate.xml-inputs").contents().length);
		
		frame.list("xml.translate.xml-inputs").selectItem(0);
		frame.button("xml.translate.xml-inputs.remove").requireEnabled();
		
		frame.button("xml.translate.xml-inputs.remove").click();
		frame.button("xml.translate.xml-inputs.remove").click();
		frame.button("xml.translate.xml-inputs.remove").click();

		frame.button("xml.translate.xml-inputs.remove").requireDisabled();
		
		frame.close();
		jEdit.closeBuffer(view(),view().getBuffer());
    }

    @Test
    public void testUpDown(){
    	final File f = new File(testData, "simple/actions.xml");
    	final File xsd = new File(testData, "simple/actions.xsd");

    	final VFSFileList selector = new VFSFileList(view(),"xml.translate.xml-inputs");
    	selector.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
    	
    	final FrameFixture frame = new FrameFixture(TestUtils.robot(),Containers.frameFor(selector));
    	frame.show();
    	
		frame.button("xml.translate.xml-inputs.up").requireDisabled();
		frame.button("xml.translate.xml-inputs.down").requireDisabled();
    	
    	
		GuiActionRunner.execute(new GuiTask(){
				protected void executeInEDT(){
					selector.setItems(new String[]{f.getPath(),xsd.getPath()});
				}
		});
		
		//no selection
		frame.button("xml.translate.xml-inputs.up").requireDisabled();
		frame.button("xml.translate.xml-inputs.down").requireDisabled();
		
		// selected last item
		frame.list("xml.translate.xml-inputs").selectItem(1);
		frame.button("xml.translate.xml-inputs.up").requireEnabled();
		frame.button("xml.translate.xml-inputs.down").requireDisabled();

		// move it on top
		frame.button("xml.translate.xml-inputs.up").click();
		assertEquals(xsd.getPath(),selector.getItem(0));
		assertEquals(f.getPath(),selector.getItem(1));
		frame.button("xml.translate.xml-inputs.up").requireDisabled();
		frame.button("xml.translate.xml-inputs.down").requireEnabled();

		// move it down
		frame.button("xml.translate.xml-inputs.down").click();
		assertEquals(f.getPath(),selector.getItem(0));
		assertEquals(xsd.getPath(),selector.getItem(1));
		frame.button("xml.translate.xml-inputs.down").requireDisabled();
    }

}
