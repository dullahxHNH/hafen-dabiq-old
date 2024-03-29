/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.awt.image.BufferedImage;

import haven.GameUI.Hidepanel;
import haven.minimap.CustomIconGroup;
import haven.minimap.CustomIconMatch;
import haven.minimap.CustomIconWnd;
import haven.tasks.*;
import haven.tasks.AutoStudy;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.*;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.image.WritableRaster;

import static haven.GameUILayout.*;
import static haven.Inventory.invsq;

public class GameUI extends ConsoleHost implements Console.Directory {
    public static final Text.Foundry msgfoundry = new Text.Foundry(Text.dfont, 14);
    private static final int blpw = 142, brpw = 142;
    public final String chrid;
    public final long plid;
    private final Hidepanel ulpanel, umpanel, urpanel, blpanel, brpanel, menupanel;
    public Avaview portrait;
    public MenuGrid menu;
    public MapView map;
    public LocalMiniMap mmap;
    public MinimapWnd mmapwnd;
    public Fightview fv;
    private List<Widget> meters = new LinkedList<Widget>();
    private List<Widget> cmeters = new LinkedList<Widget>();
    private Text lastmsg;
    private long msgtime;
    private Window invwnd, equwnd;
    public Inventory maininv;
    public CharWnd chrwdg;
    public MapWnd mapfile;
    private Widget qqview;
    private Widget qqpanel;
    public BuddyWnd buddies;
    public EquipBelt eqbelt;
    public DraggableBelt fkeybelt;
    private final Zergwnd zerg;
    public final Collection<Polity> polities = new ArrayList<Polity>();
    public HelpWnd help;
    public OptWnd opts;
    public Collection<DraggedItem> hand = new LinkedList<DraggedItem>();
    public Collection<DraggedItem> handSave = new LinkedList<DraggedItem>();
    private WItem vhand;
    public ChatUI chat;
    public ChatUI.Channel syslog;
    public double prog = -1;
    private boolean afk = false;
    @SuppressWarnings("unchecked")
    public Indir<Resource>[] belt = new Indir[144];
    public DefaultBelt beltwdg;
    public final Map<Integer, String> polowners = new HashMap<Integer, String>();
    public Bufflist buffs;
    public StudyWnd studywnd;
    public ActWnd craftwnd;
    public ActWnd buildwnd;
    public Window iconwnd;
    public final Cal cal;
    public Window deckwnd;
    public final CraftWindow makewnd;
    public TaskManager tasks;
    public ChatHidePanel chatHidePanel;
    private final GameUILayout layout;
    private boolean ignoreTrackingSound;

    public abstract class Belt extends Widget {
	public Belt(Coord sz) {
	    super(sz);
	}

	public void keyact(final int slot) {
	    if(map != null) {
		Coord mvc = map.rootxlate(ui.mc);
		if(mvc.isect(Coord.z, map.sz)) {
		    map.delay(map.new Hittest(mvc) {
			    protected void hit(Coord pc, Coord2d mc, MapView.ClickInfo inf) {
				if(inf == null)
				    GameUI.this.wdgmsg("belt", slot, 1, ui.modflags(), mc.floor(OCache.posres));
				else
				    GameUI.this.wdgmsg("belt", slot, 1, ui.modflags(), mc.floor(OCache.posres), (int)inf.gob.id, inf.gob.rc.floor(OCache.posres));
			    }
			    
			    protected void nohit(Coord pc) {
				GameUI.this.wdgmsg("belt", slot, 1, ui.modflags());
			    }
			});
		}
	    }
	}
    }
    
    @RName("gameui")
    public static class $_ implements Factory {
	public Widget create(Widget parent, Object[] args) {
	    String chrid = (String)args[0];
	    int plid = (Integer)args[1];
	    return(new GameUI(chrid, plid));
	}
    }
    
    private final Coord minimapc;
    public GameUI(String chrid, long plid) {
	this.chrid = chrid;
	this.plid = plid;
	setcanfocus(true);
	setfocusctl(true);
	chat = add(new ChatUI(0, 0));
    chat.visible = Utils.getprefb("chatvis", true);
	if (chat.visible) {
        chat.move(chat.savedpos);
	    chat.resize(chat.savedw, chat.savedh);
	}
	beltwdg.raise();
    eqbelt = add(new EquipBelt("equip", 6, 7));
    ulpanel = add(new Hidepanel("gui-ul", null, new Coord(-1, -1), false));
    umpanel = add(new Hidepanel("gui-um", null, new Coord( 0, -1), false));
	urpanel = add(new Hidepanel("gui-ur", null, new Coord( 1, -1), false));
	blpanel = add(new Hidepanel("gui-bl", null, new Coord(-1,  1), false));
	brpanel = add(new Hidepanel("gui-br", null, new Coord(1, 1), true) {
        public void move(double a) {
            super.move(a);
            menupanel.move();
        }
    });
	menupanel = add(new Hidepanel("menu", new Indir<Coord>() {
		    public Coord get() {
			return(new Coord(GameUI.this.sz.x, Math.min(brpanel.c.y - 79, GameUI.this.sz.y - menupanel.sz.y)));
		    }
		}, new Coord(1, 0), true));
	Tex lbtnbg = Resource.loadtex("gfx/hud/lbtn-bg");
	blpanel.add(new Img(Resource.loadtex("gfx/hud/blframe")), 0, lbtnbg.sz().y - 33);
	blpanel.add(new Img(lbtnbg), 0, 0);
	minimapc = new Coord(4, 34 + (lbtnbg.sz().y - 33));
	menu = brpanel.add(new MenuGrid() {
        // HACK:
        // intercept menu item usage and notify craft window about it to be able
        // to determine which crafting receipt caused creation of Makewindow
        @Override
        public boolean use(Glob.Pagina pagina) {
            boolean result = super.use(pagina);
            if (result)
                makewnd.setLastAction(pagina);
            return result;
        }}, 20, 34);
	brpanel.add(new Img(Resource.loadtex("gfx/hud/brframe")), 0, 0);
	menupanel.add(new MainMenu(), 0, 0);
	mapbuttons();
	foldbuttons();
	portrait = ulpanel.add(new Avaview(Avaview.dasz, plid, "avacam") {
		public boolean mousedown(Coord c, int button) {
		    return(true);
		}
	    }, new Coord(10, 10));
	buffs = ulpanel.add(new Bufflist(), new Coord(95, 65));
	cal = add(new Cal());
	syslog = chat.add(new ChatUI.Log("System"));
	opts = add(new OptWnd());
	opts.hide();
	zerg = add(new Zergwnd(), 187, 50);
	zerg.hide();

    studywnd = add(new StudyWnd(Coord.z), Config.studyPosition.get());
    studywnd.visible = Config.studyVisible.get();

    craftwnd = add(new ActWnd("Craft...", "paginae/craft/") {
        protected void act(Glob.Pagina pagina) {
            GameUI.this.menu.use(pagina);
        }
    });
    craftwnd.hide();
    buildwnd = add(new ActWnd("Build...", "paginae/bld/") {
        protected void act(Glob.Pagina pagina) {
            GameUI.this.menu.use(pagina);
        }
    });
    buildwnd.hide();

    iconwnd = add(new CustomIconWnd());
    iconwnd.hide();

    deckwnd = add(new DeckSelector() {
        public int getSelectedDeck() {
            FightWnd fight = GameUI.this.chrwdg.fgt.findchild(FightWnd.class);
            return (fight != null) ? fight.usesave : -1;
        }

        public void setSelectedDeck(int deckIndex) {
            FightWnd fight = GameUI.this.chrwdg.fgt.findchild(FightWnd.class);
            if (fight != null)
                fight.use(deckIndex);
        }
    });
    deckwnd.hide();

    makewnd = add(new CraftWindow(), new Coord(400, 200));
    makewnd.hide();

    chatHidePanel = add(new ChatHidePanel(chat));

    layout = new GameUILayout();
    layout.addDraggable(beltwdg, new RelativePosition(HAlign.Left, VAlign.Top, new Coord(10, 180)), false, true);
    layout.addDraggable(eqbelt, new RelativePosition(HAlign.Left, VAlign.Top, new Coord(10, 220)), false, true);
    layout.addDraggable(studywnd, new RelativePosition(HAlign.Left, VAlign.Top, new Coord(100, 100)), true, true);
    layout.addDraggable(chat, new RelativePosition(HAlign.Left, VAlign.Bottom, new Coord(15, 30)), true, true);
    }

    @Override
    protected void attach(UI ui) {
        super.attach(ui);
        ui.gui = this;
        tasks = new TaskManager(ui);
        tasks.add(new AutoStudy());
        fkeybelt = add(new CustomBelt.FKeys("custom-fkeys", ui.sess.username, ui.sess.charname));
        fkeybelt.visible = Config.showCustomFKeysBelt.get();
        layout.addDraggable(fkeybelt, new RelativePosition(HAlign.Left, VAlign.Top, new Coord(10, 280)), false, true);
    }
    public Equipory getEquipory() {
    if (equwnd != null) {
        Iterator<Equipory> iterator = equwnd.children(Equipory.class).iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        }
    }
    return null;
    }
    private void mapbuttons() {
	blpanel.add(new IButton("gfx/hud/lbtn-claim", "", "-d", "-h") {
		{tooltip = Text.render("Display personal claims");}
		public void click() {
		    if((map != null) && !map.visol(0))
			map.enol(0, 1);
		    else
			map.disol(0, 1);
		}
	    }, 0, 0);
	blpanel.add(new IButton("gfx/hud/lbtn-vil", "", "-d", "-h") {
		{tooltip = Text.render("Display village claims");}
		public void click() {
		    if((map != null) && !map.visol(2))
			map.enol(2, 3);
		    else
			map.disol(2, 3);
		}
	    }, 0, 0);
	blpanel.add(new IButton("gfx/hud/lbtn-rlm", "", "-d", "-h") {
		{tooltip = Text.render("Display realms");}
		public void click() {
		    if((map != null) && !map.visol(4))
			map.enol(4, 5);
		    else
			map.disol(4, 5);
		}
	    }, 0, 0);
	blpanel.add(new MenuButton("lbtn-map", 1, "Map ($col[255,255,0]{Ctrl+A})") {
		public void click() {
		    if((mapfile != null) && mapfile.show(!mapfile.visible)) {
			mapfile.raise();
			fitwdg(mapfile);
			setfocus(mapfile);
		    }
		}
	    });
    }

    /* Ice cream */
    private final IButton[] fold_br = new IButton[4];
    private final IButton[] fold_bl = new IButton[2];
    private void updfold(boolean reset) {
	int br;
	if(brpanel.tvis && menupanel.tvis)
	    br = 0;
	else if(brpanel.tvis && !menupanel.tvis)
	    br = 1;
	else if(!brpanel.tvis && !menupanel.tvis)
	    br = 2;
	else
	    br = 3;
	for(int i = 0; i < fold_br.length; i++)
	    fold_br[i].show(i == br);

	fold_bl[1].show(!blpanel.tvis);

	if(reset)
	    resetui();
    }

    private void foldbuttons() {
	final Tex rdnbg = Resource.loadtex("gfx/hud/rbtn-maindwn");
	final Tex rupbg = Resource.loadtex("gfx/hud/rbtn-upbg");
	fold_br[0] = new IButton("gfx/hud/rbtn-dwn", "", "-d", "-h") {
		public void draw(GOut g) {g.image(rdnbg, Coord.z); super.draw(g);}
		public void click() {
		    menupanel.cshow(false);
		    updfold(true);
		}
	    };
	fold_br[1] = new IButton("gfx/hud/rbtn-dwn", "", "-d", "-h") {
		public void draw(GOut g) {g.image(rdnbg, Coord.z); super.draw(g);}
		public void click() {
		    brpanel.cshow(false);
		    updfold(true);
		}
	    };
	fold_br[2] = new IButton("gfx/hud/rbtn-up", "", "-d", "-h") {
		public void draw(GOut g) {g.image(rupbg, Coord.z); super.draw(g);}
		public void click() {
		    menupanel.cshow(true);
		    updfold(true);
		}
		public void presize() {
		    this.c = parent.sz.sub(this.sz);
		}
	    };
	fold_br[3] = new IButton("gfx/hud/rbtn-dwn", "", "-d", "-h") {
		public void draw(GOut g) {g.image(rdnbg, Coord.z); super.draw(g);}
		public void click() {
		    brpanel.cshow(true);
		    updfold(true);
		}
	    };
	menupanel.add(fold_br[0], 0, 0);
	fold_br[0].lower();
	brpanel.adda(fold_br[1], brpanel.sz.x, 32, 1, 1);
	adda(fold_br[2], 1, 1);
	fold_br[2].lower();
	menupanel.add(fold_br[3], 0, 0);
	fold_br[3].lower();

	final Tex lupbg = Resource.loadtex("gfx/hud/lbtn-upbg");
	fold_bl[0] = new IButton("gfx/hud/lbtn-dwn", "", "-d", "-h") {
		public void click() {
		    blpanel.cshow(false);
		    updfold(true);
		}
	    };
	fold_bl[1] = new IButton("gfx/hud/lbtn-up", "", "-d", "-h") {
		public void draw(GOut g) {g.image(lupbg, Coord.z); super.draw(g);}
		public void click() {
		    blpanel.cshow(true);
		    updfold(true);
		}
		public void presize() {
		    this.c = new Coord(0, parent.sz.y - sz.y);
		}
	    };
	blpanel.add(fold_bl[0], 0, 0);
	adda(fold_bl[1], 0, 1);
	fold_bl[1].lower();

	updfold(false);
    }

    protected void added() {
	resize(parent.sz);
	ui.cons.out = new java.io.PrintWriter(new java.io.Writer() {
		StringBuilder buf = new StringBuilder();
		
		public void write(char[] src, int off, int len) {
		    List<String> lines = new ArrayList<String>();
		    synchronized(this) {
			buf.append(src, off, len);
			int p;
			while((p = buf.indexOf("\n")) >= 0) {
			    lines.add(buf.substring(0, p));
			    buf.delete(0, p + 1);
			}
		    }
		    for(String ln : lines)
			syslog.append(ln, Color.WHITE);
		}
		
		public void close() {}
		public void flush() {}
	    });
	Debug.log = ui.cons.out;
	opts.c = sz.sub(opts.sz).div(2);
    craftwnd.c = sz.sub(craftwnd.sz).div(2);
    buildwnd.c = sz.sub(buildwnd.sz).div(2);
    iconwnd.c = sz.sub(iconwnd.sz).div(2);
    }
    
    public class Hidepanel extends Widget {
	public final String id;
	public final Coord g;
	public final Indir<Coord> base;
	public boolean tvis;
	private double cur;
	private boolean persist;

	public Hidepanel(String id, Indir<Coord> base, Coord g, boolean persist) {
	    this.id = id;
	    this.base = base;
	    this.g = g;
	    this.persist = persist;
	    this.tvis = persist ? Utils.getprefb(id + "-visible", true) : true;
	    cur = show(tvis)?0:1;
	}

	public <T extends Widget> T add(T child) {
	    super.add(child);
	    pack();
	    if(parent != null)
		move();
	    return(child);
	}

	public Coord base() {
	    if(base != null) return(base.get());
	    return(new Coord((g.x > 0)?parent.sz.x:(g.x < 0)?0:(parent.sz.x / 2),
			     (g.y > 0)?parent.sz.y:(g.y < 0)?0:(parent.sz.y / 2)));
	}

	public void move(double a) {
	    cur = a;
	    Coord c = new Coord(base());
	    if(g.x < 0)
		c.x -= (int)(sz.x * a);
	    else if(g.x > 0)
		c.x -= (int)(sz.x * (1 - a));
	    if(g.y < 0)
		c.y -= (int)(sz.y * a);
	    else if(g.y > 0)
		c.y -= (int)(sz.y * (1 - a));
	    this.c = c;
	}

	public void move() {
	    move(cur);
	}

	public void presize() {
	    move();
	}

	public boolean mshow(final boolean vis) {
	    clearanims(Anim.class);
	    if(vis)
		show();
	    new NormAnim(0.25) {
		final double st = cur, f = vis?0:1;

		public void ntick(double a) {
		    if((a == 1.0) && !vis)
			hide();
		    move(st + (Utils.smoothstep(a) * (f - st)));
		}
	    };
	    tvis = vis;
	    updfold(false);
	    return(vis);
	}

	public boolean mshow() {
	    return(mshow(persist ? Utils.getprefb(id + "-visible", true) : true));
	}

	public boolean cshow(boolean vis) {
	    if (persist)
	        Utils.setprefb(id + "-visible", vis);
	    if(vis != tvis)
		mshow(vis);
	    return(vis);
	}

	public void cdestroy(Widget w) {
	    parent.cdestroy(w);
	}
    }

    static class Hidewnd extends Window {
	Hidewnd(Coord sz, String cap, boolean lg) {
	    super(sz, cap, lg);
	}

	Hidewnd(Coord sz, String cap) {
	    super(sz, cap);
	}

	public void wdgmsg(Widget sender, String msg, Object... args) {
	    if((sender == this) && msg.equals("close")) {
		this.hide();
		return;
	    }
	    super.wdgmsg(sender, msg, args);
	}
    }

    static class Zergwnd extends Hidewnd {
	Tabs tabs = new Tabs(Coord.z, Coord.z, this);
	final TButton kin, pol, pol2;

	class TButton extends IButton {
	    Tabs.Tab tab = null;
	    final Tex inv;

	    TButton(String nm, boolean g) {
		super(Resource.loadimg("gfx/hud/buttons/" + nm + "u"), Resource.loadimg("gfx/hud/buttons/" + nm + "d"));
		if(g)
		    inv = Resource.loadtex("gfx/hud/buttons/" + nm + "g");
		else
		    inv = null;
	    }

	    public void draw(GOut g) {
		if((tab == null) && (inv != null))
		    g.image(inv, Coord.z);
		else
		    super.draw(g);
	    }

	    public void click() {
		if(tab != null) {
		    tabs.showtab(tab);
		    repack();
		}
	    }
	}

	Zergwnd() {
	    super(Coord.z, "Kith & Kin", true);
	    kin = add(new TButton("kin", false));
	    kin.tooltip = Text.render("Kin");
	    pol = add(new TButton("pol", true));
	    pol2 = add(new TButton("rlm", true));
	}

	private void repack() {
	    tabs.indpack();
	    kin.c = new Coord(0, tabs.curtab.contentsz().y + 20);
	    pol.c = new Coord(kin.c.x + kin.sz.x + 10, kin.c.y);
	    pol2.c = new Coord(pol.c.x + pol.sz.x + 10, pol.c.y);
	    this.pack();
	}

	Tabs.Tab ntab(Widget ch, TButton btn) {
	    Tabs.Tab tab = add(tabs.new Tab() {
		    public void cresize(Widget ch) {
			repack();
		    }
		}, tabs.c);
	    tab.add(ch, Coord.z);
	    btn.tab = tab;
	    repack();
	    return(tab);
	}

	void dtab(TButton btn) {
	    btn.tab.destroy();
	    btn.tab = null;
	    repack();
	}

	void addpol(Polity p) {
	    /* This isn't very nice. :( */
	    TButton btn = p.cap.equals("Village")?pol:pol2;
	    ntab(p, btn);
	    btn.tooltip = Text.render(p.cap);
	}
    }

    public static class DraggedItem {
	public final GItem item;
    public final Coord dc;

	DraggedItem(GItem item, Coord dc) {
	    this.item = item; this.dc = dc;
	}
    }

    private void updhand() {
	if((hand.isEmpty() && (vhand != null)) || ((vhand != null) && !hand.contains(vhand.item))) {
	    ui.destroy(vhand);
	    vhand = null;
	}
	if(!hand.isEmpty() && (vhand == null)) {
	    DraggedItem fi = hand.iterator().next();
	    vhand = add(new ItemDrag(fi.dc, fi.item));
	}
    }

    public void addchild(Widget child, Object... args) {
	String place = ((String)args[0]).intern();
	if(place == "mapview") {
	    child.resize(sz);
		map = add((MapView)child, Coord.z);
	    map.lower();
		if (mmap != null)
			ui.destroy(mmap);
        if (mmapwnd != null) {
            ui.destroy(mmapwnd);
            layout.removeDraggable(mmapwnd);
        }
		mmap = new LocalMiniMap(Config.minimapSize.get(), map);
		mmapwnd = new MinimapWnd(Config.minimapPosition.get(), mmap.sz, map, mmap);
        layout.addDraggable(mmapwnd, new RelativePosition(HAlign.Left, VAlign.Top, new Coord(500, 100)), true, true);
		add(mmapwnd);
		mmapwnd.pack();
	} else if(place == "fight") {
	    fv = urpanel.add((Fightview)child, 0, 0);
	} else if(place == "fsess") {
	    add(child, Coord.z);
	} else if(place == "inv") {
	    invwnd = new Hidewnd(Coord.z, "Inventory") {
		    public void cresize(Widget ch) {
			pack();
		    }
		};
	    invwnd.add(maininv = (Inventory)child, Coord.z);
	    invwnd.pack();
	    invwnd.hide();
	    add(invwnd, new Coord(100, 100));
	} else if(place == "equ") {
	    equwnd = new Hidewnd(Coord.z, "Equipment");
	    equwnd.add(child, Coord.z);
	    equwnd.pack();
	    equwnd.hide();
	    add(equwnd, new Coord(400, 10));
	} else if(place == "hand") {
	    GItem g = add((GItem)child);
	    Coord lc = (Coord)args[1];
	    hand.add(new DraggedItem(g, lc));
	    updhand();
	} else if(place == "chr") {
	    chrwdg = add((CharWnd)child, new Coord(300, 50));
	    chrwdg.hide();
        // custom meter for hunger level
        if (Config.showHungerMeter.get())
            addcmeter(new HungerMeter(chrwdg.glut));
        // custom meter for FEPs
        if (Config.showFepMeter.get())
            addcmeter(new FepMeter(chrwdg.feps));
	} else if(place == "craft") {
	    makewnd.add(child);
        makewnd.pack();
        makewnd.raise();
        makewnd.show();
	} else if(place == "buddy") {
	    zerg.ntab(buddies = (BuddyWnd)child, zerg.kin);
	} else if(place == "pol") {
	    Polity p = (Polity)child;
	    polities.add(p);
	    zerg.addpol(p);
	} else if(place == "chat") {
	    chat.addchild(child);
	} else if(place == "party") {
	    add(child, 10, 95);
	} else if(place == "meter") {
	    int x = (meters.size() % 3) * (IMeter.fsz.x + 5);
	    int y = (meters.size() / 3) * (IMeter.fsz.y + 2);
	    ulpanel.add(child, portrait.c.x + portrait.sz.x + 10 + x, portrait.c.y + y);
	    meters.add(child);
        updcmeters();
	} else if(place == "buff") {
	    buffs.addchild(child);
	} else if(place == "qq") {
	    if(qqview != null) {
			qqview.reqdestroy();
			layout.removeDraggable(qqpanel);
		}
		qqview = child;
	    qqpanel = add(new DraggablePanel("questPanel", new Coord(200, 50)) {
		    public void cdestroy(Widget ch) {
			qqview = null;
			destroy();
		    }
		});
		qqpanel.add(qqview);
		layout.addDraggable(qqpanel, new RelativePosition(HAlign.Right, VAlign.Center, new Coord(10 , 10)), true, true);
	} else if(place == "misc") {
	    add(child, (Coord)args[1]);
	} else {
	    throw(new UI.UIException("Illegal gameui child", place, args));
	}
    }
    
    public void cdestroy(Widget w) {
	if(w instanceof GItem) {
	    for(Iterator<DraggedItem> i = hand.iterator(); i.hasNext();) {
		DraggedItem di = i.next();
		if(di.item == w) {
		    i.remove();
		    updhand();
		}
	    }
		for(Iterator<DraggedItem> i = handSave.iterator(); i.hasNext();) {
			DraggedItem di = i.next();
			if(di.item == w) {
				i.remove();
			}
		}
	} else if(polities.contains(w)) {
	    polities.remove(w);
	    zerg.dtab(zerg.pol);
	} else if(w == chrwdg) {
	    chrwdg = null;
	}
	if (meters.remove(w))
        updcmeters();
    cmeters.remove(w);
    }
    
    private static final Resource.Anim progt = Resource.local().loadwait("gfx/hud/prog").layer(Resource.animc);
    private Tex curprog = null;
    private int curprogf, curprogb;
    private void drawprog(GOut g, double prog) {
	int fr = Utils.clip((int)Math.floor(prog * progt.f.length), 0, progt.f.length - 2);
	int bf = Utils.clip((int)(((prog * progt.f.length) - fr) * 255), 0, 255);
	if((curprog == null) || (curprogf != fr) || (curprogb != bf)) {
	    if(curprog != null)
		curprog.dispose();
	    WritableRaster buf = PUtils.imgraster(progt.f[fr][0].sz);
	    PUtils.blit(buf, progt.f[fr][0].img.getRaster(), Coord.z);
	    PUtils.blendblit(buf, progt.f[fr + 1][0].img.getRaster(), Coord.z, bf);
	    BufferedImage img = PUtils.rasterimg(buf);

        if (Config.showHourglassPercentage.get()) {
            BufferedImage txt = Text.renderstroked(String.format("%d%%", (int) (100 * prog)), Color.WHITE, Color.BLACK).img;
            img.getGraphics().drawImage(txt, 24 - txt.getWidth() / 2, 9 - txt.getHeight() / 2, null);
        }

	    curprog = new TexI(img); curprogf = fr; curprogb = bf;
	}
	g.aimage(curprog, new Coord(sz.x / 2, (sz.y * 4) / 10), 0.5, 0.5);
    }

    public void draw(GOut g) {
    if (Config.screenshotMode) {
        if (map != null)
            map.draw(g);
        return;
    }
	super.draw(g);
	if(prog >= 0)
	    drawprog(g, prog);
	int by = sz.y;
	if(chat.visible)
	    by = Math.min(by, chat.c.y);
	if(cmdline != null) {
	    drawcmd(g, new Coord(chat.c.x, by -= 20));
	} else if(lastmsg != null) {
	    if((System.currentTimeMillis() - msgtime) > 3000) {
		lastmsg = null;
	    } else {
		g.chcolor(0, 0, 0, 192);
		g.frect(new Coord(chat.c.x, by - 25), lastmsg.sz().add(8, 4));
		g.chcolor();
		g.image(lastmsg.tex(), new Coord(chat.c.x + 5, by -= 23));
	    }
	}
	if(!chat.visible) {
	    chat.drawsmall(g, new Coord(chat.c.x, by), 50);
	}
    }
    
    public void tick(double dt) {
	super.tick(dt);
	if(!afk && (System.currentTimeMillis() - ui.lastevent > 300000)) {
	    afk = true;
	    wdgmsg("afk");
	} else if(afk && (System.currentTimeMillis() - ui.lastevent < 300000)) {
	    afk = false;
	}
    tasks.tick(dt);
    }
    
    public void uimsg(String msg, Object... args) {
	if(msg == "err") {
	    String err = (String)args[0];
	    error(err);
	} else if(msg == "msg") {
	    String text = (String)args[0];
	    msg(text);
	} else if(msg == "prog") {
	    if(args.length > 0)
		prog = ((Number)args[0]).doubleValue() / 100.0;
	    else
		prog = -1;
	} else if(msg == "setbelt") {
	    int slot = (Integer)args[0];
	    if(args.length < 2) {
		belt[slot] = null;
	    } else {
		belt[slot] = ui.sess.getres((Integer)args[1]);
	    }
	} else if(msg == "polowner") {
	    int id = (Integer)args[0];
	    String o = (String)args[1];
	    boolean n = ((Integer)args[2]) != 0;
	    if(o != null)
		o = o.intern();
	    String cur = polowners.get(id);
	    if(map != null) {
		if((o != null) && (cur == null)) {
		    map.setpoltext(id, "Entering " + o);
		} else if((o == null) && (cur != null)) {
		    map.setpoltext(id, "Leaving " + cur);
		}
	    }
	    polowners.put(id, o);
	} else if(msg == "showhelp") {
	    Indir<Resource> res = ui.sess.getres((Integer)args[0]);
	    if(help == null)
		help = adda(new HelpWnd(res), 0.5, 0.5);
	    else
		help.res = res;
	} else if(msg == "map-mark") {
	    long gobid = ((Integer)args[0]) & 0xffffffff;
	    long oid = (Long)args[1];
	    Indir<Resource> res = ui.sess.getres((Integer)args[2]);
	    String nm = (String)args[3];
	    if(mapfile != null)
		mapfile.markobj(gobid, oid, res, nm);
	} else {
	    super.uimsg(msg, args);
	}
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == menu) {
	    wdgmsg(msg, args);
	    return;
	} else if((sender == chrwdg) && (msg == "close")) {
	    chrwdg.hide();
	    return;
	} else if((sender == mapfile) && (msg == "close")) {
	    mapfile.hide();
	    return;
	} else if((sender == help) && (msg == "close")) {
	    ui.destroy(help);
	    help = null;
	    return;
	}
	super.wdgmsg(sender, msg, args);
    }

    private void fitwdg(Widget wdg) {
	if(wdg.c.x < 0)
	    wdg.c.x = 0;
	if(wdg.c.y < 0)
	    wdg.c.y = 0;
	if(wdg.c.x + wdg.sz.x > sz.x)
	    wdg.c.x = sz.x - wdg.sz.x;
	if(wdg.c.y + wdg.sz.y > sz.y)
	    wdg.c.y = sz.y - wdg.sz.y;
    }

    public static class MenuButton extends IButton {
	private final int gkey;

	MenuButton(String base, int gkey, String tooltip) {
	    super("gfx/hud/" + base, "", "-d", "-h");
	    this.gkey = (char)gkey;
	    this.tooltip = RichText.render(tooltip, 0);
	}

	public void click() {}

	public boolean globtype(char key, KeyEvent ev) {
	    if((gkey != -1) && (key == gkey)) {
		click();
		return(true);
	    }
	    return(super.globtype(key, ev));
	}
    }

    private static final Tex menubg = Resource.loadtex("gfx/hud/rbtn-bg");
    public class MainMenu extends Widget {
	public MainMenu() {
	    super(menubg.sz());
	    add(new MenuButton("rbtn-inv", 9, "Inventory ($col[255,255,0]{Tab})") {
		    public void click() {
			if((invwnd != null) && invwnd.show(!invwnd.visible)) {
			    invwnd.raise();
			    fitwdg(invwnd);
			}
		    }
		}, 0, 0);
	    add(new MenuButton("rbtn-equ", 5, "Equipment ($col[255,255,0]{Ctrl+E})") {
		    public void click() {
			if((equwnd != null) && equwnd.show(!equwnd.visible)) {
			    equwnd.raise();
			    fitwdg(equwnd);
			}
		    }
		}, 0, 0);
	    add(new MenuButton("rbtn-chr", 20, "Character Sheet ($col[255,255,0]{Ctrl+T})") {
		    public void click() {
			if((chrwdg != null) && chrwdg.show(!chrwdg.visible)) {
			    chrwdg.raise();
			    fitwdg(chrwdg);
			}
		    }
		}, 0, 0);
	    add(new MenuButton("rbtn-bud", 2, "Kith & Kin ($col[255,255,0]{Ctrl+B})") {
		    public void click() {
			if(zerg.show(!zerg.visible)) {
			    zerg.raise();
			    fitwdg(zerg);
			    setfocus(zerg);
			}
		    }
		}, 0, 0);
	    add(new MenuButton("rbtn-opt", 15, "Options ($col[255,255,0]{Ctrl+O})") {
		    public void click() {
			if(opts.show(!opts.visible)) {
			    opts.raise();
			    fitwdg(opts);
			    setfocus(opts);
			}
		    }
		}, 0, 0);
	}

	public void draw(GOut g) {
	    g.image(menubg, Coord.z);
	    super.draw(g);
	}
    }

    public boolean globtype(char key, KeyEvent ev) {
	if(key == ':') {
	    entercmd();
	    return(true);
	} else if(key == ' ') {
	    toggleui();
	    return(true);
	} else if(key == 3) {
	    if(chat.visible && !chat.hasfocus) {
		setfocus(chat);
	    } else {
		if(chat.sz.y == 0) {
		    chat.resize(chat.savedw, chat.savedh);
		    setfocus(chat);
		} else {
		    chat.resize(0, 0);
		}
	    }
	    Utils.setprefb("chatvis", chat.targeth != 0);
	} else if((key == 27) && (map != null) && !map.hasfocus) {
	    setfocus(map);
	    return(true);
	} else if (key != 0) {
        boolean alt = ev.isAltDown();
        int keycode = ev.getKeyCode();
        if (alt && keycode >= KeyEvent.VK_0 && keycode <= KeyEvent.VK_9) {
            beltwdg.setCurrentBelt(Utils.floormod(keycode - KeyEvent.VK_0 - 1, 10));
            return true;
        } else if (alt && keycode == KeyEvent.VK_S) {
            studywnd.show(!studywnd.visible);
            if (studywnd.visible)
                studywnd.raise();
            return true;
        } else if (alt && keycode == KeyEvent.VK_M) {
            if (mmapwnd != null) {
                mmapwnd.togglefold();
                return true;
            }
        } else if (alt && keycode == KeyEvent.VK_C) {
            craftwnd.show(!craftwnd.visible);
            if (craftwnd.visible)
                craftwnd.raise();
            return true;
        } else if (alt && keycode == KeyEvent.VK_B) {
            buildwnd.toggle();
            if (buildwnd.visible)
                buildwnd.raise();
            return true;
        } else if (alt && keycode == KeyEvent.VK_N) {
            Config.nightvision.set(!Config.nightvision.get());
        } else if (alt && keycode == KeyEvent.VK_G) {
            if (map != null)
                map.gridOverlay.setVisible(!map.gridOverlay.isVisible());
            return true;
        } else if (alt && keycode == KeyEvent.VK_R) {
            if (mmap != null)
                mmap.toggleCustomIcons();
            return true;
        } else if (alt && keycode == KeyEvent.VK_D) {
            if (map != null)
                map.toggleGobRadius();
            return true;
        } else if (alt && keycode == KeyEvent.VK_Q) {
            Config.showQuality.set(!Config.showQuality.get());
            return true;
        } else if (alt && keycode == KeyEvent.VK_K) {
            deckwnd.show(!deckwnd.visible);
            deckwnd.c = new Coord(sz.sub(deckwnd.sz).div(2));
            if (deckwnd.visible)
                deckwnd.raise();
            return true;
        } else if (alt && keycode == KeyEvent.VK_F) {
            if (map != null) {
                map.toggleFriendlyFire();
                msg("Friendly fire prevention is now turned " + (map.isPreventFriendlyFireEnabled() ? "on" : "off"));
            }
            return true;
        } else if (alt && keycode == KeyEvent.VK_I) {
            Config.showGobInfo.set(!Config.showGobInfo.get());
            return true;
        } else if (alt && keycode == KeyEvent.VK_W) {
            Config.screenshotMode = !Config.screenshotMode;
            return true;
        } else if (alt && keycode == KeyEvent.VK_T) {
            Config.disableTileTransitions.set(!Config.disableTileTransitions.get());
            ui.sess.glob.map.rebuild();
            return true;
        } else if (keycode == KeyEvent.VK_Q && ev.getModifiers() == 0) {
            // get all forageables from config
            List<String> names = new ArrayList<String>();
            for (CustomIconGroup group : ui.sess.glob.icons.config.groups) {
                if ("Forageables".equals(group.name)) {
                    for (CustomIconMatch match : group.matches)
                        if (match.show)
                            names.add(match.value);
                    break;
                }
            }
            tasks.add(new Forager(11 * Config.autopickRadius.get(), 1, names.toArray(new String[names.size()])));
            return true;
        } else if (keycode == KeyEvent.VK_W && ev.getModifiers() == 0) {
            tasks.add(new Drunkard());
            return true;
        } else if (ev.isShiftDown() && keycode == KeyEvent.VK_I) {
            Config.hideKinInfoForNonPlayers.set(!Config.hideKinInfoForNonPlayers.get());
            return true;
        } else if (ev.isControlDown() && keycode == KeyEvent.VK_H) {
            Config.hideModeEnabled.set(!Config.hideModeEnabled.get());
            synchronized (ui.sess.glob.oc) {
                for (Gob gob : this.ui.sess.glob.oc) {
                    this.ui.sess.glob.oc.changed(gob);
                }
            }
            return true;
        } else if (alt && keycode == KeyEvent.VK_P) {
            Config.showGobPaths.set(!Config.showGobPaths.get());
            return true;
        }
    }
	return(super.globtype(key, ev));
    }
    
    public boolean mousedown(Coord c, int button) {
	return(super.mousedown(c, button));
    }

    private int uimode = 1;
    public void toggleui(int mode) {
	Hidepanel[] panels = {blpanel, brpanel, ulpanel, umpanel, urpanel, menupanel};
	switch(uimode = mode) {
	case 0:
	    for(Hidepanel p : panels)
		p.mshow(true);
	    break;
	case 1:
	    for(Hidepanel p : panels)
		p.mshow();
	    break;
	case 2:
	    for(Hidepanel p : panels)
		p.mshow(false);
	    break;
	}
    }

    public void resetui() {
	Hidepanel[] panels = {brpanel, ulpanel, umpanel, urpanel, menupanel};
	for(Hidepanel p : panels)
	    p.cshow(p.tvis);
	uimode = 1;
    }

    public void toggleui() {
	toggleui((uimode + 1) % 3);
    }

    public void resize(Coord sz) {
	this.sz = sz;
    chatHidePanel.c = new Coord(0, sz.y - chatHidePanel.sz.y);
	if(map != null)
	    map.resize(sz);
    iconwnd.c = sz.sub(iconwnd.sz).div(2);
    cal.c = new Coord((sz.x - cal.sz.x) / 2, 10);
    layout.update(sz);
	super.resize(sz);
    }
    
    public void presize() {
	resize(parent.sz);
    }
    
    public void msg(String msg, Color color, Color logcol) {
	msgtime = System.currentTimeMillis();
	lastmsg = msgfoundry.render(msg, color);
	syslog.append(msg, logcol);
    }

    public void msg(String msg, Color color) {
	msg(msg, color, color);
    }

    private static final Resource errsfx = Resource.local().loadwait("sfx/error");
    private long lasterrsfx = 0;
    public void error(String msg) {
	msg(msg, new Color(192, 0, 0), new Color(255, 0, 0));
	long now = System.currentTimeMillis();
	if(now - lasterrsfx > 100) {
	    Audio.play(errsfx);
	    lasterrsfx = now;
	}
    }

    private static final Resource msgsfx = Resource.local().loadwait("sfx/msg");
    private long lastmsgsfx = 0;
    public void msg(String msg) {
	msg(msg, Color.WHITE, Color.WHITE);
	Audio.play(msgsfx);
    }

    public void notification(String format, Object... args) {
        msg(String.format(format, args), Color.WHITE);
    }
    
    public void act(String... args) {
	wdgmsg("act", (Object[])args);
    }

    public void act(int mods, Coord mc, Gob gob, String... args) {
	int n = args.length;
	Object[] al = new Object[n];
	System.arraycopy(args, 0, al, 0, n);
	if(mc != null) {
	    al = Utils.extend(al, al.length + 2);
	    al[n++] = mods;
	    al[n++] = mc;
	    if(gob != null) {
		al = Utils.extend(al, al.length + 2);
		al[n++] = (int)gob.id;
		al[n++] = gob.rc;
	    }
	}
	wdgmsg("act", al);
    }

    public class FKeyBelt extends Belt implements DTarget, DropTarget {
	public final int beltkeys[] = {KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4,
				       KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8,
				       KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12};
	public int curbelt = 0;

	public FKeyBelt() {
	    super(new Coord(450, 34));
	}

	private Coord beltc(int i) {
	    return(new Coord(((invsq.sz().x + 2) * i) + (10 * (i / 4)), 0));
	}
    
	private int beltslot(Coord c) {
	    for(int i = 0; i < 12; i++) {
		if(c.isect(beltc(i), invsq.sz()))
		    return(i + (curbelt * 12));
	    }
	    return(-1);
	}
    
	public void draw(GOut g) {
	    for(int i = 0; i < 12; i++) {
		int slot = i + (curbelt * 12);
		Coord c = beltc(i);
		g.image(invsq, beltc(i));
		try {
		    if(belt[slot] != null)
			g.image(belt[slot].get().layer(Resource.imgc).tex(), c.add(1, 1));
		} catch(Loading e) {}
		g.chcolor(156, 180, 158, 255);
		FastText.aprintf(g, c.add(invsq.sz().sub(2, 0)), 1, 1, "F%d", i + 1);
		g.chcolor();
	    }
	}
	
	public boolean mousedown(Coord c, int button) {
	    int slot = beltslot(c);
	    if(slot != -1) {
		if(button == 1)
		    GameUI.this.wdgmsg("belt", slot, 1, ui.modflags());
		if(button == 3)
		    GameUI.this.wdgmsg("setbelt", slot, 1);
		return(true);
	    }
	    return(false);
	}

	public boolean globtype(char key, KeyEvent ev) {
	    if(key != 0)
		return(false);
	    boolean M = (ev.getModifiersEx() & (KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK)) != 0;
	    for(int i = 0; i < beltkeys.length; i++) {
		if(ev.getKeyCode() == beltkeys[i]) {
		    if(M) {
			curbelt = i;
			return(true);
		    } else {
			keyact(i + (curbelt * 12));
			return(true);
		    }
		}
	    }
	    return(false);
	}
	
	public boolean drop(Coord c, Coord ul) {
	    int slot = beltslot(c);
	    if(slot != -1) {
		GameUI.this.wdgmsg("setbelt", slot, 0);
		return(true);
	    }
	    return(false);
	}

	public boolean iteminteract(Coord c, Coord ul) {return(false);}
	
	public boolean dropthing(Coord c, Object thing) {
	    int slot = beltslot(c);
	    if(slot != -1) {
		if(thing instanceof Resource) {
		    Resource res = (Resource)thing;
		    if(res.layer(Resource.action) != null) {
			GameUI.this.wdgmsg("setbelt", slot, res.name);
			return(true);
		    }
		}
	    }
	    return(false);
	}
    }
    
    private static final Tex nkeybg = Resource.loadtex("gfx/hud/hb-main");
    public class NKeyBelt extends Belt implements DTarget, DropTarget {
	public int curbelt = 0;
	final Coord pagoff = new Coord(5, 25);

	public NKeyBelt() {
	    super(nkeybg.sz());
	}

	private Coord beltc(int i) {
	    return(pagoff.add(((invsq.sz().x + 2) * i) + (10 * (i / 5)), 0));
	}
    
	private int beltslot(Coord c) {
	    for(int i = 0; i < 10; i++) {
		if(c.isect(beltc(i), invsq.sz()))
		    return(i + (curbelt * 12));
	    }
	    return(-1);
	}
    
	public void draw(GOut g) {
	    g.image(nkeybg, Coord.z);
	    for(int i = 0; i < 10; i++) {
		int slot = i + (curbelt * 12);
		Coord c = beltc(i);
		g.image(invsq, beltc(i));
		try {
		    if(belt[slot] != null)
			g.image(belt[slot].get().layer(Resource.imgc).tex(), c.add(1, 1));
		} catch(Loading e) {}
		g.chcolor(156, 180, 158, 255);
		FastText.aprintf(g, c.add(invsq.sz().sub(2, 0)), 1, 1, "%d", (i + 1) % 10);
		g.chcolor();
	    }
	    super.draw(g);
	}
	
	public boolean mousedown(Coord c, int button) {
	    int slot = beltslot(c);
	    if(slot != -1) {
		if(button == 1)
		    GameUI.this.wdgmsg("belt", slot, 1, ui.modflags());
		if(button == 3)
		    GameUI.this.wdgmsg("setbelt", slot, 1);
		return(true);
	    }
	    return(super.mousedown(c, button));
	}

	public boolean globtype(char key, KeyEvent ev) {
	    if(key != 0)
		return(false);
	    int c = ev.getKeyCode();
	    if((c < KeyEvent.VK_0) || (c > KeyEvent.VK_9))
		return(false);
	    int i = Utils.floormod(c - KeyEvent.VK_0 - 1, 10);
	    boolean M = (ev.getModifiersEx() & (KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK)) != 0;
	    if(M) {
		curbelt = i;
	    } else {
		keyact(i + (curbelt * 12));
	    }
	    return(true);
	}
	
	public boolean drop(Coord c, Coord ul) {
	    int slot = beltslot(c);
	    if(slot != -1) {
		GameUI.this.wdgmsg("setbelt", slot, 0);
		return(true);
	    }
	    return(false);
	}

	public boolean iteminteract(Coord c, Coord ul) {return(false);}
	
	public boolean dropthing(Coord c, Object thing) {
	    int slot = beltslot(c);
	    if(slot != -1) {
		if(thing instanceof Resource) {
		    Resource res = (Resource)thing;
		    if(res.layer(Resource.action) != null) {
			GameUI.this.wdgmsg("setbelt", slot, res.name);
			return(true);
		    }
		}
	    }
	    return(false);
	}
    }
    
    {
	String val = Utils.getpref("belttype", "n");
	if(val.equals("n")) {
	    beltwdg = add(new DefaultBelt.NKeys("default"));
	} else if(val.equals("f")) {
	    beltwdg = add(new DefaultBelt.FKeys("default"));
	} else {
	    beltwdg = add(new DefaultBelt.NKeys("default"));
	}
    }

    public void addcmeter(Widget meter) {
        ulpanel.add(meter);
        cmeters.add(meter);
        updcmeters();
    }

    public <T extends Widget> void delcmeter(Class<T> cl) {
        Widget widget = null;
        for (Widget meter : cmeters) {
            if (cl.isAssignableFrom(meter.getClass())) {
                widget = meter;
                break;
            }
        }
        if (widget != null) {
            cmeters.remove(widget);
            widget.destroy();
            updcmeters();
        }
    }

    private void updcmeters() {
        int i = 0;
        for (Widget meter : cmeters) {
            int x = ((meters.size() + i) % 3) * (IMeter.fsz.x + 5);
            int y = ((meters.size() + i) / 3) * (IMeter.fsz.y + 2);
            meter.c = new Coord(portrait.c.x + portrait.sz.x + 10 + x, portrait.c.y + y);
            i++;
        }
    }

    public void swapHand() {
        if (hand.isEmpty()) {
            hand.addAll(handSave);
            handSave.clear();
            updhand();
        } else {
            handSave.addAll(hand);
            hand.clear();
            updhand();
        }
    }

    @Override
    public void bound() {
        if (Config.toggleTracking.get()) {
            act("tracking");
            ignoreTrackingSound = true;
        }
    }

    public void actBelt(final int slot, boolean checkMapHit) {
        if (checkMapHit) {
            MapView map = ui.gui.map;
            if (map == null)
                return;
            Coord mvc = map.rootxlate(ui.mc);
            if(mvc.isect(Coord.z, map.sz)) {
                map.delay(map.new Hittest(mvc) {
                    protected void hit(Coord pc, Coord2d mc, MapView.ClickInfo inf) {
                        if (inf == null)
                            ui.gui.wdgmsg("belt", slot, 1, ui.modflags(), mc);
                        else
                            ui.gui.wdgmsg("belt", slot, 1, ui.modflags(), mc, (int) inf.gob.id, inf.gob.rc);
                    }

                    protected void nohit(Coord pc) {
                        ui.gui.wdgmsg("belt", slot, 1, ui.modflags());
                    }
                });
            }
        } else
            wdgmsg("belt", slot, 1, ui.modflags());

        if (belt[slot] != null) {
            makewnd.setLastAction(new Glob.Pagina(belt[slot]));
        }
    }

    public void refreshProgress() {
        curprog = null;
    }

    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
    {
    cmdmap.put("kinfile", new Console.Command() {
        public void run(Console cons, String[] args) throws Exception {
            try {
                StringBuffer path = new StringBuffer();
                for (int i = 1; i < args.length; i++) {
                    path.append(args[i]);
                    if (i < args.length - 1)
                        path.append(" ");
                }
                if (path.length() > 0) {
                    List<String> lines = FileUtils.readLines(new File(path.toString()));
                    // use task that will add kins trying not to spam server
                    tasks.add(new AddKinsTask(lines));
                }
            } catch (Exception e) {
                error(e.getMessage());
            }
        }
    });
	cmdmap.put("afk", new Console.Command() {
		public void run(Console cons, String[] args) {
		    afk = true;
		    wdgmsg("afk");
		}
	    });
	cmdmap.put("act", new Console.Command() {
		public void run(Console cons, String[] args) {
		    Object[] ad = new Object[args.length - 1];
		    System.arraycopy(args, 1, ad, 0, ad.length);
		    wdgmsg("act", ad);
		}
	    });
	cmdmap.put("belt", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if(args[1].equals("f")) {
			beltwdg.destroy();
			beltwdg = add(new DefaultBelt.FKeys("default"));
			Utils.setpref("belttype", "f");
			resize(sz);
		    } else if(args[1].equals("n")) {
			beltwdg.destroy();
			beltwdg = add(new DefaultBelt.NKeys("default"));
			Utils.setpref("belttype", "n");
			resize(sz);
		    }
		}
	    });
	cmdmap.put("tool", new Console.Command() {
		public void run(Console cons, String[] args) {
		    add(gettype(args[1]).create(GameUI.this, new Object[0]), 200, 200);
		}
	    });
    }
    public Map<String, Console.Command> findcmds() {
	return(cmdmap);
    }
}
