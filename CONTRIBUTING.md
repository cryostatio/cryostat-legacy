This is a simple guide of the workflow being used in Cryostat. We will use https://github.com/cryostatio/cryostat as an example.

## Set up SSH with Github

See https://docs.github.com/en/authentication/connecting-to-github-with-ssh. If you prefer to use `https`, you can skip this step.

## Fork the upstream repository

See https://docs.github.com/en/get-started/quickstart/fork-a-repo

## Local changes

Clone the forked repository.
```bash
$ git clone git@github.com:<username>/cryostat.git
$ git remote add upstream git@github.com:cryostatio/cryostat.git # Adding upstream as remote
```

Navigate to `cryostat` directory and checkout a new branch.
```bash
$ cd cryostat && git checkout -b some-task-branch
```

Edit some files and commits your changes.
```bash
$ git add . # Stage your changes. Here, we stage the entire repo (recursively).
$ git commit -m "<type>(<scope>): a nice description of your work"
```

## Open a PR

Push your changes to your remote respository (forked). You might need to rebase on the latest commit on the upstream's `main`.

To rebase:
```bash
$ git fetch upstream # Fetch upstream commits
$ git rebase upstream/main # Rebase on latest commit on upstream/main
```

To push:
```bash
$ git push -u origin some-task-branch # First time push
# or
$ git push -f origin some-task-branch # If already pushed to origin remote
```

Visit upstream repository and open a PR.

![visit-upstream](https://user-images.githubusercontent.com/68053619/193327052-9322651f-d325-429b-88d9-34133571282c.png)

Compare changes across forks (your branch in fork to the upstream main).

![select-compare-across-forks](https://user-images.githubusercontent.com/68053619/193328282-79d45b5e-097f-4995-ae21-51592dcf0bd4.jpg)


Add: 
- A title following [guidelines](https://www.conventionalcommits.org/en/v1.0.0/).
- A description with a referenced issue that the PR is for.
- A label (depending on your PR scope).
- A list of reviewers (at least one approval is required).

**Note**: You might not be able to add reviewers and labels (if not in Cryostatio organization). Please reach out to a project owner.

![prepare-pull-requests](https://user-images.githubusercontent.com/68053619/193328096-9df163d8-18ae-4978-b3c2-928ba4ad7872.jpg)

Once its done, you can click `Create Pull Request` and wait for approval.

![pull-requests-approved](https://user-images.githubusercontent.com/68053619/193328530-3cde0298-0254-4587-947a-43d51d02878f.png)

## Result

Your changes will be get squashed into a single commit on `main` with the message including your PR title, and branch commit messages. From there, you can safely delete your branch.

To delete:
```bash
$ git checkout main # Must checkout a different branch before deleting the current checked out one
$ git branch -D some-task-branch # Delete local branch with force or without force using -d
$ git push --delete origin some-task-branch # Delete branch on origin (forked)
```

Now, you can rebase your local and origin `main` with latest upstream:
```bash
$ git fetch upstream
$ git checkout main
$ git rebase upstream/main
$ git push origin main # Update origin remote
```