package pl.viko.itemalchemyaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.pitan76.itemalchemy.EMCManager;

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
     *
     * @param dispatcher the server command dispatcher
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("itemalchemyaddon")
                .then(CommandManager.literal("reloademc")
                        .executes(ReloadEmcCommand::reloadEmc)
                )
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
    private static int reloadEmc(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        source.sendMessage(Text.literal("Recalculating EMC from recipes..."));

        RecipeManager recipeManager = source.getServer().getRecipeManager();
        DynamicRegistryManager registryManager = source.getServer().getRegistryManager();

        List<Recipe<?>> allRecipes = new ArrayList<>();
        allRecipes.addAll(recipeManager.listAllOfType(RecipeType.CRAFTING));
        allRecipes.addAll(recipeManager.listAllOfType(RecipeType.SMELTING));
        allRecipes.addAll(recipeManager.listAllOfType(RecipeType.BLASTING));
        allRecipes.addAll(recipeManager.listAllOfType(RecipeType.SMOKING));
        allRecipes.addAll(recipeManager.listAllOfType(RecipeType.CAMPFIRE_COOKING));

        source.sendMessage(Text.literal("Found " + allRecipes.size() + " recipes to process"));

        List<Recipe<?>> processedRecipes = new ArrayList<>();
        List<Recipe<?>> unprocessedRecipes = new ArrayList<>(allRecipes);
        int successfulCalculations = 0;
        boolean changesMadeInPass;

        int pass = 0;
        do {
            pass++;
            changesMadeInPass = false;
            List<Recipe<?>> remainingRecipes = new ArrayList<>();

            for (Recipe<?> recipe : unprocessedRecipes) {
                if (processedRecipes.contains(recipe)) {
                    continue;
                }

                ItemStack output = recipe.getOutput(registryManager);
                if (output.isEmpty()) {
                    processedRecipes.add(recipe);
                    continue;
                }

                if (pass == 1) {
                    source.sendMessage(Text.literal("Processing recipe: " + recipe.getId()
                            + " -> " + output.getItem().getName().getString()));
                }

                if (EMCManager.get(output.getItem()) > 0) {
                    processedRecipes.add(recipe);
                    continue;
                }

                if (calculateEmcFromRecipe(output, recipe)) {
                    processedRecipes.add(recipe);
                    changesMadeInPass = true;
                    successfulCalculations++;
                    source.sendMessage(Text.literal("Added EMC for: "
                            + output.getName().getString() + " = " + EMCManager.get(output.getItem())));
                } else {
                    remainingRecipes.add(recipe);
                }
            }
            unprocessedRecipes = remainingRecipes;
        } while (changesMadeInPass && pass < 10);

        source.sendMessage(Text.literal("EMC recalculation complete. Computed values for "
                + successfulCalculations + " new items."));
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
    private static boolean calculateEmcFromRecipe(ItemStack output, Recipe<?> recipe) {
        long totalEmc = 0;
        boolean allIngredientsHaveEmc = true;

        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] matchingStacks = ingredient.getMatchingStacks();
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
