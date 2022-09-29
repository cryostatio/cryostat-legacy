This is a simple guide of the workflow being used in Cryostat. We will use https://github.com/cryostatio/cryostatio.github.io as an example.

## Fork the upstream repository

See https://docs.github.com/en/get-started/quickstart/fork-a-repo

## Local changes

Clone the forked repository.
```bash
$ git clone git@github.com:<username>/cryostatio.github.io.git
$ git remote add upstream git@github.com:cryostatio/cryostatio.github.io.git # Adding upstream as remote
```

Checkout another branch.
```bash
$ cd cryostatio.github.io && git checkout -b some-task-branch
```

Edit some files and commits your changes.
```bash
$ git add . # Stage your changes. Here, we stage the entire repo (recursively).
$ git commit -m "some very nice message"
```

## Open a PR

Push your changes to your remote respository (forked). You might need to rebase on the newest commit on the upstream.

To rebase:
```bash
$ git fetch upstream # Fetch upstream commits
$ git checkout main && git rebase upstream/main && git push origin main # Checkout main, merge upstream and update origin
```

To push:
```bash
$ git checkout some-task-branch
$ git push -u origin some-task-branch
```

Visit upstream repository and open a PR.

![visit-upstream](https://user-images.githubusercontent.com/68053619/178812137-ee9c1fe3-2f78-4671-8bdc-41c0a55565d7.png)

Compare changes across forks (your branch in fork to the upstream main).

![select-compare-across-forks](https://user-images.githubusercontent.com/68053619/178812907-ea709be4-265a-4f4b-9127-80e4eaf97089.png)

Add: 
- A title following [guidelines](https://www.conventionalcommits.org/en/v1.0.0/).
- A description with a referenced issue that the PR is for.
- A label (depending on your PR scope).
- A list of reviewers (at least one approval is required).

**Note**: You might not be able to add reviewers and labels (if not in Cryostatio organization). Please reach out to a project owner.

![prepare-pull-requests](https://user-images.githubusercontent.com/68053619/178813336-f9f7d94d-b17b-40ef-aec9-2340639c3e90.png)

Once its done, you can click `Create Pull Request` and wait for approval.

![pull-requests-approved](https://user-images.githubusercontent.com/68053619/178813993-d453236d-5dd1-45f0-b124-b75b3a0c70e6.png)

## Result

Your changes will be get squashed into a single commit on `main` with the message including your PR title, and branch commit messages. From there, you can safely delete your branch.

To delete:
```bash
$ git checkout main # Must checkout a different branch before deleting the current checked out one
$ git branch -D some-task-branch # Delete local branch with force or without force using -d
$ git push --delete origin some-task-branch # Delete branch on origin (forked)
```