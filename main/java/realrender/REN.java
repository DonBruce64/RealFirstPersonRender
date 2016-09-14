package realrender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.registry.EntityRegistry;

@Mod(modid = REN.MODID, name = REN.MODNAME, version = REN.MODVER)
public class REN {
	public static final String MODID="rfpr";
	public static final String MODNAME="Real First-Person Render";
	public static final String MODVER="7.1.0";	
	private static final REN instance = new REN();
	
	private static boolean customItemOverride;
	private static boolean wasF1DownLastTick;
	/**Mode for RFPR
	 * First bit shows HUD (replacement for F1 functionality).
	 * Second bit disables player body (regular hand shown).
	 * Third bit activates shaders mode.
	 */
	private static byte mode;
	private static byte spawnDelay = 100;
	private static EntityPlayerDummy dummy;
	private static String[] overrideItems; 

	@EventHandler
	public void PreInit(FMLPreInitializationEvent event){
		this.initModMetadata(event);
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();
		overrideItems = config.get(Configuration.CATEGORY_GENERAL, "OverrideItems", new String[]{"map", "compass", "clock"}, "When these items are held, RFPR will temporally stop rendering.").getStringList();
		config.save();
	}
	
	@EventHandler
	public void Init(FMLInitializationEvent event){
		FMLCommonHandler.instance().bus().register(instance);
		MinecraftForge.EVENT_BUS.register(instance);
		EntityRegistry.registerModEntity(EntityPlayerDummy.class, "PlayerDummy", 0, MODID, 5, 100, false);
		RenderingRegistry.registerEntityRenderingHandler(EntityPlayerDummy.class, new RenderPlayerDummy());
	}
	
	private void initModMetadata(FMLPreInitializationEvent event){
        ModMetadata meta = event.getModMetadata();
        meta.name = this.MODNAME;
        meta.description = "Small mod that adds in full-body rendering.";
        meta.authorList.clear();
        meta.authorList.add("don_bruce");
        
        meta.modId = this.MODID;
        meta.version = this.MODVER;
        meta.autogenerated = false;
	}
	
	@SubscribeEvent
	public void on(TickEvent.RenderTickEvent event){
		if(event.phase.equals(Phase.START)){
			if(Keyboard.isKeyDown(Keyboard.KEY_F1)){
				if(!wasF1DownLastTick){
					if(Minecraft.getMinecraft().thePlayer.isSneaking()){
						mode = (byte) ((mode & 3) + (~mode & 4));
						Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("SHADERS MODE: " + (mode >= 4 ? "ENABLED" : "DISABLED")));
					}else{						
						mode = (byte) ((mode & 3) == 3 ? (mode & 4) : (mode & 4) + (mode & 3) + 1);
					}
				}
				wasF1DownLastTick = true;
			}else{
				wasF1DownLastTick = false;
			}			
			Minecraft.getMinecraft().gameSettings.hideGUI = (mode & 1) == 1;
		}
	}
	
	@SubscribeEvent
	public void on(RenderHandEvent event){
		event.setCanceled((mode & 2) != 2);
	}
	
	@SubscribeEvent
	public void on(RenderWorldLastEvent event){
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		customItemOverride = false;
		if(player.inventory.getCurrentItem() != null){
			for(String itemName : overrideItems){
				if(itemName.equals(player.inventory.getCurrentItem().getUnlocalizedName().substring(5))){
					customItemOverride = true;
					break;
				}
			}
		}
		
		//Shaders mode enabled.
		if(mode >= 4){
			if(customItemOverride){
				Minecraft.getMinecraft().gameSettings.hideGUI = false;
			}else{
				Minecraft mc = Minecraft.getMinecraft();
	            final ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
	            int i = scaledresolution.getScaledWidth();
	            int j = scaledresolution.getScaledHeight();
	            final int k = Mouse.getX() * i / mc.displayWidth;
	            final int l = j - Mouse.getY() * j / mc.displayHeight - 1;
				GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
				Minecraft.getMinecraft().ingameGUI.renderGameOverlay(event.partialTicks, mc.currentScreen != null, k, l);
			}
		}
		
		if(dummy == null){
		      if(spawnDelay == 0){
		    	  dummy = new EntityPlayerDummy(Minecraft.getMinecraft().theWorld);
		    	  Minecraft.getMinecraft().theWorld.spawnEntityInWorld(dummy);
		    	  dummy.setPositionAndRotation(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch);
		      }else{
		        --spawnDelay;
		      }
		}else if(dummy.worldObj.provider.dimensionId != player.worldObj.provider.dimensionId || dummy.getDistanceSqToEntity(player) > 5){
			dummy.setDead();
			dummy = null;
			spawnDelay = 100;
		}
	}
	
	public class EntityPlayerDummy extends Entity{
		public EntityPlayerDummy(World world){
			super(world);
			this.ignoreFrustumCheck = true;
		}
		public void onUpdate(){
			EntityPlayer player = Minecraft.getMinecraft().thePlayer;
			dummy.setPositionAndRotation(player.posX, player.posY - player.height, player.posZ, player.rotationYaw, player.rotationPitch);
		}
		protected void entityInit(){}
		protected void readEntityFromNBT(NBTTagCompound p_70037_1_){}
		protected void writeEntityToNBT(NBTTagCompound p_70014_1_){}
	};
	
	public class RenderPlayerDummy extends Render{
		@Override
		public void doRender(Entity entity, double x, double y, double z, float yaw, float ticks){
			if(Minecraft.getMinecraft().gameSettings.thirdPersonView == 0 && !((mode & 2) == 2) && !customItemOverride){
				EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
				if(player.inventory.armorInventory[2] != null && player.inventory.armorInventory[2].getItem().getUnlocalizedName().toLowerCase().contains("elytra") && Keyboard.isKeyDown(Keyboard.KEY_SPACE)){return;}
				RenderPlayer playerRenderer = ((RenderPlayer) this.renderManager.getEntityRenderObject(player));
				playerRenderer.modelBipedMain.bipedHead.isHidden = true;
				playerRenderer.modelBipedMain.bipedEars.isHidden = true;
				playerRenderer.modelBipedMain.bipedHeadwear.isHidden = true;
				//Temporarily remove helmet prior to rendering.
				ItemStack helmetStack = player.inventory.armorInventory[3];
				player.inventory.armorInventory[3] = null;
				if(player.isPlayerSleeping()){
					playerRenderer.doRender(player, player.posX - entity.posX + x, player.posY - entity.posY + y, player.posZ - entity.posZ + z, player.renderYawOffset, ticks);
				}else{
					playerRenderer.doRender(player, player.posX - entity.posX + x + 0.35*Math.sin(Math.toRadians(player.renderYawOffset)), player.posY - entity.posY + y, player.posZ - entity.posZ + z - 0.35*Math.cos(Math.toRadians(player.renderYawOffset)), player.renderYawOffset, ticks);
				}
				player.inventory.armorInventory[3] = helmetStack;
				playerRenderer.modelBipedMain.bipedHead.isHidden = false;
				playerRenderer.modelBipedMain.bipedEars.isHidden = false;
				playerRenderer.modelBipedMain.bipedHeadwear.isHidden = false;
			}
		}
		
		/*180+METHOD
		@Override
		public void doRender(Entity entity, double x, double y, double z, float yaw, float ticks){
			if(Minecraft.getMinecraft().gameSettings.thirdPersonView == 0 && !((mode & 2) == 2) && !customItemOverride){
				EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
				if(player.inventory.armorInventory[2] != null && player.inventory.armorInventory[2].getItem().getUnlocalizedName().toLowerCase().contains("elytra") && Keyboard.isKeyDown(Keyboard.KEY_SPACE)){return;}
				RenderPlayer playerRenderer = ((RenderPlayer) this.renderManager.getEntityRenderObject(player));
				ModelPlayer playerModel = (ModelPlayer) playerRenderer.getMainModel();
				playerModel.bipedHead.isHidden = true;
				playerModel.bipedHeadwear.isHidden = true;
				//Temporarily remove helmet prior to rendering.
				ItemStack helmetStack = player.inventory.armorInventory[3];;
				player.inventory.armorInventory[3] = null;
				if(player.isPlayerSleeping()){
					playerRenderer.doRender(player, player.posX - entity.posX + x, player.posY - entity.posY + y, player.posZ - entity.posZ + z, player.renderYawOffset, ticks);
				}else{
					playerRenderer.doRender(player, player.posX - entity.posX + x + 0.35*Math.sin(Math.toRadians(player.renderYawOffset)), player.posY - entity.posY + y, player.posZ - entity.posZ + z - 0.35*Math.cos(Math.toRadians(player.renderYawOffset)), player.renderYawOffset, ticks);
				}
				player.inventory.armorInventory[3] = helmetStack;
				playerModel.bipedHead.isHidden = false;
				playerModel.bipedHeadwear.isHidden = false;
			}
		}
		180+METHOD*/

		@Override
		protected ResourceLocation getEntityTexture(Entity entity){
			return null;
		}
	};
	/*189+METHOD	
	public class RENRenderingFactory implements IRenderFactory{
		private final Class<? extends Render> entityRender;
		public RENRenderingFactory(Class<? extends Render>  entityRender){
			this.entityRender = entityRender;
		}
		
		@Override
		public Render createRenderFor(RenderManager manager){
			try{
				return entityRender.getConstructor(RenderManager.class).newInstance(manager);
			}catch(Exception e){
				e.printStackTrace();
				return null;
			}
		}
	};
	189+METHOD*/
}
