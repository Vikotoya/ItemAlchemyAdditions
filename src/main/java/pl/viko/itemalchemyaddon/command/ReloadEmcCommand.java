package pl.viko.itemalchemyaddon.command;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeType;
import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.mcpitanlib.api.command.CommandRegistry;
import net.pitan76.mcpitanlib.api.command.CommandSettings;
import net.pitan76.mcpitanlib.api.command.LiteralCommand;
import net.pitan76.mcpitanlib.api.event.ServerCommandEvent;
import net.pitan76.mcpitanlib.api.util.CompatIdentifier;
import net.pitan76.mcpitanlib.api.util.IngredientUtil;
import net.pitan76.mcpitanlib.api.util.RecipeUtil;
import net.pitan76.mcpitanlib.api.util.RegistryLookupUtil;
import net.pitan76.mcpitanlib.midohra.recipe.ServerRecipeManager;
import net.pitan76.mcpitanlib.midohra.recipe.entry.RecipeEntry;
import net.pitan76.mcpitanlib.midohra.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the {@code /itemalchemyaddon reloademc} command.
 *
 * <p>When executed, this command iterates over all crafting, smelting, blasting,
 * smoking, and campfire-cooking recipes and attempts to derive EMC values for
 * items that do not already have one.  The algorithm runs in multiple passes
 * (up to 10) so that items whose ingredients only gain an EMC value in a later
 * pass can still be resolved.</p>
 */
public class ReloadEmcCommand {

    /**
     * Registers the {@code /itemalchemyaddon reloademc} sub-command.
     */
    public static void register() {
        CommandRegistry.register("itemalchemyaddon", new LiteralCommand() {
            @Override
            public void init(CommandSettings settings) {
                super.init(settings);
                addArgumentCommand("reloademc", new LiteralCommand() {
                    @Override
                    public void execute(ServerCommandEvent e) {
                        reloadEmc(e);
                    }
                });
            }

            @Override
            public void execute(ServerCommandEvent e) {
                e.sendSuccess("Usage: /itemalchemyaddon reloademc");
                e.sendSuccess("Recalculates EMC values for items based on their recipes.");
            }
                }
//                .then(CommandManager.literal("reloademc")
//                        .executes(ReloadEmcCommand::reloadEmc)
//                )
        );
    }

    /**
     * Executes the EMC recalculation from recipes.
     *
     * <p>The algorithm works as follows:</p>
     * <ol>
     *   <li>Collect every recipe of the supported types.</li>
     *   <li>For each recipe whose output does not yet have an EMC value,
     *       attempt to compute one from the EMC values of its ingredients.</li>
     *   <li>Repeat until no new values are discovered or 10 passes are reached,
     *       allowing transitive resolution.</li>
     * </ol>
     *
     * @return {@code 1} (success) — always
     */
    private static int reloadEmc(ServerCommandEvent e) {
//        ServerCommandSource source = e.getSource();
        e.sendSuccess("Recalculating EMC from recipes...");

        ServerRecipeManager recipeManager = World.of(e.getWorld()).toServerWorld().get().getRecipeManager();

        List<RecipeEntry> allRecipes = new ArrayList<>();
        allRecipes.addAll(recipeManager.toMinecraft().listAllOfType(RecipeType.CRAFTING).stream().map(r -> RecipeEntry.of(r, CompatIdentifier.fromMinecraft(r.getId()))).toList());
        allRecipes.addAll(recipeManager.toMinecraft().listAllOfType(RecipeType.SMELTING).stream().map(r -> RecipeEntry.of(r, CompatIdentifier.fromMinecraft(r.getId()))).toList());
        allRecipes.addAll(recipeManager.toMinecraft().listAllOfType(RecipeType.BLASTING).stream().map(r -> RecipeEntry.of(r, CompatIdentifier.fromMinecraft(r.getId()))).toList());
        allRecipes.addAll(recipeManager.toMinecraft().listAllOfType(RecipeType.SMOKING).stream().map(r -> RecipeEntry.of(r, CompatIdentifier.fromMinecraft(r.getId()))).toList());
        allRecipes.addAll(recipeManager.toMinecraft().listAllOfType(RecipeType.CAMPFIRE_COOKING).stream().map(r -> RecipeEntry.of(r, CompatIdentifier.fromMinecraft(r.getId()))).toList());

        e.sendSuccess("Found " + allRecipes.size() + " recipes to process");

        List<RecipeEntry> processedRecipes = new ArrayList<>();
        List<RecipeEntry> unprocessedRecipes = new ArrayList<>(allRecipes);
        int successfulCalculations = 0;
        boolean changesMadeInPass;

        int pass = 0;
        do {
            pass++;
            changesMadeInPass = false;
            List<RecipeEntry> remainingRecipes = new ArrayList<>();

            for (RecipeEntry recipe : unprocessedRecipes) {
                if (processedRecipes.contains(recipe)) {
                    continue;
                }

                ItemStack output = RecipeUtil.getOutput(recipe.toMinecraft(), RegistryLookupUtil.getRegistryLookup(e.getWorld()));
                if (output.isEmpty()) {
                    processedRecipes.add(recipe);
                    continue;
                }

                if (pass == 1) {
                    e.sendSuccess("Processing recipe: " + recipe.getId()
                            + " -> " + output.getItem().getName().getString());
                }

                if (EMCManager.get(output.getItem()) > 0) {
                    processedRecipes.add(recipe);
                    continue;
                }

                if (calculateEmcFromRecipe(output, recipe)) {
                    processedRecipes.add(recipe);
                    changesMadeInPass = true;
                    successfulCalculations++;
                    e.sendSuccess("Added EMC for: "
                            + output.getName().getString() + " = " + EMCManager.get(output.getItem()));
                } else {
                    remainingRecipes.add(recipe);
                }
            }
            unprocessedRecipes = remainingRecipes;
        } while (changesMadeInPass && pass < 10);

        e.sendSuccess("EMC recalculation complete. Computed values for "
                + successfulCalculations + " new items.");
        return 1;
    }

    /**
     * Attempts to calculate and assign an EMC value for the given recipe output
     * based on the EMC values of its ingredients.
     *
     * <p>For each ingredient slot the method picks the first matching stack that
     * already has an EMC value.  If every ingredient can be resolved this way,
     * the total is divided by the output count and stored via
     * {@link EMCManager#set}.</p>
     *
     * @param output the recipe output stack
     * @param recipe the recipe to evaluate
     * @return {@code true} if a new EMC value was successfully assigned
     */
    private static boolean calculateEmcFromRecipe(ItemStack output, RecipeEntry recipe) {
        long totalEmc = 0;
        boolean allIngredientsHaveEmc = true;

        for (Ingredient ingredient : recipe.getRecipe().getInputs()) {
            ItemStack[] matchingStacks = IngredientUtil.getMatchingStacks(ingredient);
            if (matchingStacks.length == 0) {
                continue;
            }

            boolean ingredientHasEmc = false;
            long ingredientEmc = 0;
            int ingredientCount = 0;

            for (ItemStack stack : matchingStacks) {
                if (EMCManager.contains(stack.getItem())) {
                    ingredientHasEmc = true;
                    ingredientEmc = EMCManager.get(stack.getItem());
                    ingredientCount = stack.getCount();
                    break;
                }
            }

            if (!ingredientHasEmc) {
                allIngredientsHaveEmc = false;
                break;
            }

            totalEmc += ingredientEmc * ingredientCount;
        }

        if (allIngredientsHaveEmc && totalEmc > 0) {
            int outputCount = output.getCount();
            long finalEmc = totalEmc / outputCount;

            if (finalEmc > 0) {
                EMCManager.set(output.getItem(), finalEmc);
                return true;
            }
        }

        return false;
    }
}
